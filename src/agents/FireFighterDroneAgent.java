package CGGC;

import SimSetUp.ScenarioConfig;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class FireFighterDroneAgent extends Agent {
    ScenarioConfig sconfig = new ScenarioConfig();

    private AID[] collAgent;
    float BLEVEL = (float) sconfig.getLobFFD();
    Random r = new Random();
    double initcon;
    double initrec;
    private List<String> flipvalues = new ArrayList<>();

    List<String> rqstring;
    DFAgentDescription[] result;
    DFAgentDescription template_dfd = new DFAgentDescription();
    ServiceDescription template_sd = new ServiceDescription();
    ACLMessage inform;
    String cntrl = "";
    float blevel;
    int step = 0;
    int app_count = 0;
    int totalservicegoalvalue = 0;
    private Map<String, Integer> ctrlapp = new HashMap<String, Integer>();// control which action is approved to how many times
    boolean flg = true;

    protected void setup() {
        //
        ServiceDescription sd;
        DFAgentDescription dfd = new DFAgentDescription();
        for (String service : sconfig.getServiceeFFD()) {
            sd = new ServiceDescription();
            sd.setType(service);
            sd.setName(getLocalName());
            dfd.addServices(sd);
        }
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        //to control flipping decisions
        for (int i = 0; i < sconfig.getServiceeFFD().size(); i++)
            for (int j = 0; j < sconfig.getAction_type_size(); j++) {
                String value = sconfig.getServiceeFFD().get(i) + ", " + sconfig.getActionName(j) + ", " + 0;
                flipvalues.add(value);
            }
        //control service completion condition
        for (int i = 0; i < sconfig.getServiceeFFD().size(); i++) {
            totalservicegoalvalue += sconfig.getGoal_value(sconfig.getServiceeFFD().get(i).trim());
            ctrlapp.put(sconfig.getServiceeFFD().get(i), 0);
        }
        totalservicegoalvalue = totalservicegoalvalue * sconfig.getAction_type_size();
        //setup initial con and rec values
        initcon = (float) Math.round((0 + (1 - 0) * r.nextDouble()) * 100000d) / 100000d;
        initrec = (float) Math.round((0 + (1 - 0) * r.nextDouble()) * 100000d) / 100000d;
        blevel = BLEVEL;

        addBehaviour(new DecisionMakingLogic());
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("==>" + getLocalName() + " app number: " + ctrlapp);
        System.out.println(getLocalName() + " is terminating.");
    }

    //decision making logic
    private class DecisionMakingLogic extends Behaviour {
        public void action() {

            MessageTemplate mt;
            ACLMessage msg_content;
            float con = 0;
            float rec = 0;

            switch (step) {
                case 0: //get request
                    mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
                    msg_content = myAgent.receive(mt);
                    if (msg_content != null) {
                        cntrl = "";
                        rqstring = new ArrayList<String>(Arrays.asList(msg_content.getContent().replaceAll("\\[|\\]", "").split(",")));
                        System.out.println("Action recomm ( " + myAgent.getLocalName() + ": " + rqstring + ")");
                        //find manager

                        con = Float.parseFloat(rqstring.get(1).trim());
                        rec = Float.parseFloat(rqstring.get(2).trim());
                        step++;
                    } else {
                        DFAgentDescription temp = new DFAgentDescription();
                        ServiceDescription temp_sd = new ServiceDescription();
                        temp_sd.setType("manager");
                        temp.addServices(temp_sd);
                        try {
                            DFAgentDescription[] result = DFService.search(myAgent, temp);
                            if (result.length > 0) {
                                block();
                                break;
                            } else {
                                step = 3;
                                myAgent.doDelete();
                                break;
                            }

                        } catch (FIPAException fe) {
                            fe.printStackTrace();
                        }

                    }
                    //break;
                case 1: // search reciever
                    template_sd.setType("manager");
                    template_dfd.addServices(template_sd);

                    try {
                        result = DFService.search(myAgent, template_dfd);
                        inform = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        if (result.length > 0) {
                            inform.addReceiver(result[0].getName());
                            for (int i = 0; i < sconfig.getServiceeFFD().size(); i++) {
                                if (sconfig.getServiceeFFD().get(i).trim().equals(rqstring.get(0).trim())) {
                                    int currgoal = Integer.parseInt(rqstring.get(4).trim());
                                    int completedtask = (sconfig.getGoal_value(rqstring.get(0).trim()) - currgoal);

                                    int val = 1;
                                    float mean = 1;
                                    for (Map.Entry<String, Integer> key : ctrlapp.entrySet()) {//considering the respective count of app for service
                                        if (key.getKey().startsWith(rqstring.get(0).trim())) {
                                            val = key.getValue();
                                        }
                                    }
                                    float kvalue = (float) val;// (completedtask));//contribution sofar

                                    if (completedtask > 0) {
                                        //kvalue = ((float) val / (completedtask * sconfig.getAction_type_size()));//contribution sofar
                                        mean = (float) (completedtask / sconfig.getFFDNUM());
                                    }
                                    float kprimevalue = (float) currgoal;// / sconfig.getGoal_value(rqstring.get(0).trim());//level of remaining task
                                    List<String> decision = new ArrayList<>();
                                    decision.add(sconfig.getServiceeFFD().get(i));
                                    decision.add(rqstring.get(3));
                                    decision.add(String.valueOf(blevel));
                                    decision.add(String.valueOf(rqstring.get(1)));
                                    decision.add(String.valueOf(rqstring.get(2)));
                                    decision.add(myAgent.getLocalName());
                                    System.out.println(myAgent.getLocalName() + ": k value " + kvalue + " kprime value :" + kprimevalue);

                                    if (completedtask == 0 && flg) {
                                        cntrl = "APPROVE";
                                        flg = false;
                                    } else if ((kvalue < (float) sconfig.getV() * mean && kprimevalue > sconfig.getV() * sconfig.getV() * sconfig.getGoal_value(rqstring.get(0).trim())) ||
                                            (kvalue <= (float) sconfig.getVprime() * mean && kvalue >= (float) sconfig.getV() * mean && kprimevalue > sconfig.getV() * sconfig.getV() * sconfig.getGoal_value(rqstring.get(0).trim()) && BLEVEL > sconfig.getVprime() * sconfig.getLobCommand()) ||
                                            (kvalue <= (float) sconfig.getVprime() * mean && kvalue >= (float) sconfig.getV() * mean && BLEVEL <= sconfig.getVprime() * sconfig.getLobCommand() && BLEVEL >= sconfig.getV() * sconfig.getLobCommand() && kprimevalue > sconfig.getV() * sconfig.getGoal_value(rqstring.get(0).trim())) ||
                                            (kprimevalue <= sconfig.getV() * sconfig.getGoal_value(rqstring.get(0).trim()) && kprimevalue >= sconfig.getV() * sconfig.getV() * sconfig.getGoal_value(rqstring.get(0).trim()) && kvalue > (float) sconfig.getVprime() * mean && BLEVEL > sconfig.getVprime() * sconfig.getLobCommand())) {
                                        cntrl = "APPROVE";
                                    } else {
                                        cntrl = "NOT";
                                    }

                                    if (cntrl.equals("NOT")) {
                                        if (initcon >= con && initrec >= rec) {
                                            decision.add("APPROVE");
                                            cntrl = "APPROVE";
                                            if (blevel >= sconfig.getVprime() * sconfig.getLobCommand()) {
                                                boolean var = false;
                                                for (int k = 0; k < flipvalues.size(); k++) {
                                                    String[] str = flipvalues.get(k).split(",");
                                                    String flipstr;
                                                    for (int l = 0; l < str.length; l++) {
                                                        if (str[0].trim().equals(rqstring.get(0).trim()) &&
                                                                str[1].trim().equals(rqstring.get(3).trim()) &&
                                                                str[2].trim().equals("0")) {
                                                            str[2] = "1";
                                                            flipstr = str[0].trim() + ", " + str[1].trim() + ", " + str[2];
                                                            flipvalues.set(k, flipstr);
                                                            var = true;
                                                            break;
                                                        }
                                                    }
                                                    if (var)
                                                        break;
                                                }
                                            } else if (blevel < sconfig.getLobCommand()) {
                                                if (r.nextDouble() > 0.6)//60% chance
                                                    if (blevel < sconfig.getLobCommand())
                                                        blevel += 0.1;
                                            }
                                        } else if (initcon >= con || initrec >= rec) {
                                            decision.add("APPROVE");
                                            cntrl = "APPROVE";
                                        } else {
                                            cntrl = "NOT";
                                        }
                                        if (cntrl.equals("NOT")) {
                                            boolean flag = false;
                                            for (int k = 0; k < flipvalues.size(); k++) {
                                                String[] str = flipvalues.get(k).split(",");
                                                String flipstr;
                                                for (int l = 0; l < str.length; l++) {
                                                    if (str[0].trim().equals(rqstring.get(0).trim()) &&
                                                            str[1].trim().equals(rqstring.get(3).trim()) &&
                                                            str[2].trim().equals("1")) {
                                                        flag = true;
                                                        break;
                                                    }
                                                }
                                                if (flag)
                                                    break;
                                            }
                                            if (flag == true)
                                                decision.add("APPROVE");
                                            else
                                                decision.add("NOT");

                                        }
                                    } else {
                                        decision.add("APPROVE");
                                        cntrl = "APPROVE";
                                    }

                                    inform.setContent(decision.toString());
                                    myAgent.send(inform);
                                    if (cntrl.equals("APPROVE"))
                                        decision.removeIf(name -> name.equals("APPROVE"));
                                    else
                                        decision.removeIf(name -> name.equals("NOT"));
                                    step = 0;
                                    break;
                                } else {
                                    block();
                                }
                            }
                        } else {
                            step++;
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                case 2: // get confirmation
                    MessageTemplate mtconfirm = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    ACLMessage msgconfirm = myAgent.receive(mtconfirm);
                    if (msgconfirm != null) {
                        List<String> cont = new ArrayList<String>(Arrays.asList(msgconfirm.getContent().replaceAll("\\[|\\]", "").split(",")));

                        if (msgconfirm.getSender().getLocalName().trim().equals("SoSManager") &&
                                myAgent.getLocalName().equals(cont.get(0).trim())) {
                            System.out.println("Task accomplishment confirmation " + msgconfirm.getSender().getLocalName().trim() + " to " + msgconfirm.getContent());
                            // System.out.println("=======" + app_count);
                            for (Map.Entry<String, Integer> key : ctrlapp.entrySet()) {//counting number of approval for each service
                                if (key.getKey().startsWith(cont.get(1).trim())) {
                                    try {
                                        ctrlapp.put(cont.get(1).trim(), ctrlapp.get(cont.get(1).trim()) + 1);
                                    } catch (NullPointerException npe) {
                                        npe.printStackTrace();
                                    }
                                }
                            }
                        }
                    }

                    try {
                        MessageTemplate prop = MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE);
                        ACLMessage msgprop = myAgent.receive(prop);
                        if (msgprop != null) {
                            if (msgprop.getSender().getLocalName().trim().equals("SoSManager") &&
                                    myAgent.getLocalName().equals(msgprop.getContent().trim())) {
                                step++;
                                myAgent.doDelete();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //
                    DFAgentDescription temp = new DFAgentDescription();
                    ServiceDescription temp_sd = new ServiceDescription();
                    temp_sd.setType("manager");
                    temp.addServices(temp_sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, temp);
                        if (result.length > 0) {
                            step = 0;
                        } else {
                            step++;
                            myAgent.doDelete();
                            break;
                        }

                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

            }
        }

        public boolean done() {
            return step == 3;
        }
    }
}
