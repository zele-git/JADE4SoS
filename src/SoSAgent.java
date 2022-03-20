package SimSetUp;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SoSAgent extends Agent {
    ScenarioConfig sconfig = new ScenarioConfig();
    private AID[] collAgent;
    private AID[] collAgent2;// to propagate message
    List<String> rprt = new ArrayList<>();
    List<String> actionreport = new ArrayList<>();

    private Map<String, Integer> goal_value = sconfig.getGoal_value();
    private Map<String, List<String>> workflow = sconfig.getWorkflow();

    List<String> rqstdata = new ArrayList<>();
    String taskstring = "";
    String prvstring = ""; //to keep track of which task executed in the previous message

    Random r = new Random();

    int keyvalue = 0;
    FileWriter writer = null;
    FileWriter writer2 = null;
    long startTime;
    List<String> timestamp = new ArrayList<>();
    boolean flag = false;
    int step = 0;

    protected void setup() {
        // register services
        startTime = System.currentTimeMillis();

        DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription selfservice = new ServiceDescription();

        dfd.setName(getAID());
        selfservice.setName(getLocalName());
        selfservice.setType("manager");
        dfd.addServices(selfservice);
        //System.out.println("service type added: " + dfd.getName());
        try {

            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new CommunicatePolicyAction());
        // addBehaviour(new CSVReader());
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        //mainMenu.dispose();
        String collect = rprt.stream().collect(Collectors.joining()).replaceAll("\\[|\\]", "");
        try {
            writer = new FileWriter("C:/Users/User/Pictures/JADE MCIR/dataset.csv");
            writer.write(collect);
            writer.close();
            //System.out.println("Written ____");
            long endTime = System.currentTimeMillis();
            //System.out.println(goal_value);

            System.out.println("That took " + (endTime - startTime) + " milliseconds");
            System.out.println(timestamp);

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Execution report \n");
        System.out.println("Manager " + getAID().getName() + " terminating.\n");

    }

    // communicate manager intentions
    private class CommunicatePolicyAction extends Behaviour {
        List<String> services = new ArrayList<>();
        List<String> recom_action = sconfig.getAction_type();
        List<String> tasklist; //

        public void action() {
            //System.out.println("List of collaboration behavior table: \n" + coll_behavior);
            System.out.println("List of goal value table: \n" + goal_value);
            switch (step) {
                case 0://determine unsatisfied goal, keep the sequence of tasks
                    Set<String> keySet = goal_value.keySet();
                    tasklist = new ArrayList<>(keySet);//contains updated tasks after removing those accomplished
                    int size = tasklist.size();
                    if (prvstring.trim().equals("")) {
                        //start from first index of workflow
                        Set<String> keys = workflow.keySet();
                        List<String> tasks = new ArrayList<>(keys);
                        taskstring = tasks.get(0);
                    } else {
                        //select from possible next task of prvstring
                        if (tasklist.contains(prvstring)) {
                            List<String> entries = new ArrayList<>();
                            for (Map.Entry<String, List<String>> pair : workflow.entrySet()) {
                                if (pair.getKey().equals(prvstring))
                                    entries = pair.getValue();
                            }
                            if (entries.isEmpty()) {
                                taskstring = prvstring;
                            } else {
                                if (tasklist.contains(entries.get(new Random().nextInt(entries.size()))))
                                    taskstring = entries.get(new Random().nextInt(entries.size()));
                                else {
                                    sconfig.modifyworkflow(prvstring, entries.get(new Random().nextInt(entries.size())));
                                    taskstring = tasklist.get(new Random().nextInt(tasklist.size()));//report may have been finished
                                }
                            }

                        } else {
                            sconfig.modifyworkflow(prvstring);
                            taskstring = tasklist.get(new Random().nextInt(tasklist.size()));//report may have been finished
                        }
                    }
                    try {
                        keyvalue = goal_value.get(taskstring.trim());
                    } catch (Exception e) {
                        step = 0;
                        break;
                    }

                    List<Integer> tempval = new ArrayList<>();
                    for (int i = 0; i < size; i++)// size of tasklist which contains heads the workflows
                        tempval.add(goal_value.get(tasklist.get(i)));
                    rprt.add(tempval.toString() + "\n");
                    if (keyvalue > 0) {
                        step++;
                        break;
                    } else {
                        //long tstamp = System.currentTimeMillis();
                        //if (!timestamp.contains(taskstring))
                        //timestamp.add(taskstring + ", " + (tstamp - startTime));
                        //oal_value.remove(taskstring);
                        step = 0;
                        break;
                    }

                case 1: // compose request message to broadcast
                    services.add(taskstring);
                    DFAgentDescription template_dfd = new DFAgentDescription();
                    ServiceDescription template_sd = new ServiceDescription();
                    for (String service : services)
                        template_sd.setType(service);
                    template_dfd.addServices(template_sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template_dfd);
                        if (result.length > 0) {
                            collAgent = new AID[result.length];
                            for (int i = 0; i < result.length; i++) {
                                collAgent[i] = result[i].getName();
                                //System.out.println(result[i].getName().getLocalName());
                            }
                            step++;
                        } else {
                            block();
                            break;
                        }

                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                case 2://broadcast message
                    String action = recom_action.get(new Random().nextInt(recom_action.size()));
                    //System.out.println("Action selected :" + action);
                    ACLMessage rqst = new ACLMessage(ACLMessage.CFP);
                    try {
                        BufferedReader br = new BufferedReader(new FileReader("C:/Users/User/Pictures/JADE MCIR/pdataset.csv"));
                        if (collAgent.length > 0) {
                            for (int i = 0; i < collAgent.length; ++i) {
                                rqst.addReceiver(collAgent[i]);

                                //add request data including con and rec value
                                rqstdata.clear();
                                rqstdata.add(taskstring);
                                //read input from CSV file and compute average con and rec
                                List<String> records = new ArrayList<>();
                                //DecimalFormat df = new DecimalFormat("#.#####");
                                int count = 0;
                                float con_least = 1;
                                float rec_least = 1;
                                String line;
                                while ((line = br.readLine()) != null) {
                                    records.add(line);
                                }
                                // System.out.println("Dataset rows: " + records.size());
                                for (String val : records) {//select the least value so that the agent will confirm if its value is bigger
                                    String[] value = val.split(",");
                                    if (value[0].trim().equals(taskstring) && value[1].trim().equals(action) && value[6].trim().equals("APPROVE") &&
                                            value[5].trim().startsWith(collAgent[i].getLocalName())) {
                                        float tempcon = Float.parseFloat(value[3].trim());
                                        float temprec = Float.parseFloat(value[4].trim());
                                        if (tempcon <= con_least)
                                            con_least = tempcon;
                                        if (temprec <= rec_least)
                                            rec_least = temprec;
                                    }
                                }

                                //rec_average = rec_average / records.size();

                                // System.out.println("Selected task: " + taskstring);
                                // System.out.println("Average value of con and rec: " + con_average + " and " + rec_average);
                                if (con_least == 1)
                                    rqstdata.add((float) Math.round((0 + (1 - 0) * r.nextDouble()) * 100000d) / 100000d + "");
                                else
                                    rqstdata.add(con_least + "");
                                if (rec_least == 1)
                                    rqstdata.add((float) Math.round((0 + (1 - 0) * r.nextDouble()) * 100000d) / 100000d + "");
                                else
                                    rqstdata.add(rec_least + "");
                                rqstdata.add(action);
                                if (goal_value.get(taskstring) != null)
                                    rqstdata.add(goal_value.get(taskstring).toString());
                                else
                                    rqstdata.add("0");
                                rqst.setContent(rqstdata.toString());
                                myAgent.send(rqst);
                            }
                            step++;
                            break;
                        } else {
                            block();
                            break;
                        }

                    } catch (IOException io) {
                        System.out.println(io);
                    }

                case 3://collect response
                    MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        List<String> rspmessage = new ArrayList<String>(Arrays.asList(reply.getContent().replaceAll("\\[|\\]", "").split(",")));
                        actionreport.add(rspmessage.toString() + "\n");//archieving decisions
                        String collect2 = actionreport.stream().collect(Collectors.joining()).replaceAll("\\[|\\]", "");
                        if (goal_value.get(rspmessage.get(0)) != null && goal_value.get(rspmessage.get(0)) > 0) {
                            try {
                                writer2 = new FileWriter("C:/Users/User/Pictures/JADE MCIR/pdataset.csv");
                                writer2.write(collect2);
                                writer2.close();

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            System.out.println("Decision: \n (" + rspmessage + ")");
                            //step++;
                        }
                        if (rspmessage.get(0).trim().equals(taskstring) && rspmessage.get(6).trim().equals("APPROVE")) {
                            ACLMessage confirm = new ACLMessage(ACLMessage.INFORM);
                            List<String> cont = new ArrayList<>();

                            confirm.addReceiver(reply.getSender());
                            cont.add(reply.getSender().getLocalName());
                            cont.add(taskstring);
                            confirm.setContent(cont.toString());
                            myAgent.send(confirm);
                            keyvalue--;
                            if (keyvalue > 0) {
                                goal_value.put(taskstring, keyvalue);
                                prvstring = taskstring;//to maintain task execution flow
                            }
                            if (keyvalue == 0) {
                                goal_value.remove(taskstring);
                                //Set<String> keySettemp = goal_value.keySet();
                                //tasklist = new ArrayList<>(keySettemp);
                                prvstring = taskstring; //tasklist.get(new Random().nextInt(tasklist.size()));//report may have been finished
                                long tstamp = System.currentTimeMillis();
                                if (!timestamp.contains(taskstring))
                                    timestamp.add(taskstring + ", " + (tstamp - startTime));
                            }
                            //step++;
                        }

                    }
                    step++;
                case 4://propagating
                    if ((goal_value.containsKey("search") && goal_value.get("search") > 0) ||
                            (goal_value.containsKey("rescue") && goal_value.get("rescue") > 0) ||
                            (goal_value.containsKey("firstaid") && goal_value.get("firstaid") > 0) ||
                            (goal_value.containsKey("transport") && goal_value.get("transport") > 0) ||
                            (goal_value.containsKey("report") && goal_value.get("report") > 0)) {
                        step = 0;
                        break;
                    } else {
//                            long tstamp = System.currentTimeMillis();
//                            if (!timestamp.contains(taskstring))
//                                timestamp.add(taskstring + ", " + (tstamp - startTime));
                        //inform to all agent that all task are accomplished
                        boolean alive = true;
                        DFAgentDescription temp;
                        ServiceDescription temp_sd;
                        DFAgentDescription[] result;
//                        do {
                            try {
                                temp = new DFAgentDescription();
                                temp_sd = new ServiceDescription();
                                for (String service : sconfig.getCapabilities())
                                    temp_sd.setType(service);
                                temp.addServices(temp_sd);

                                result = DFService.search(myAgent, temp);
                                if (result.length > 0) {
                                    collAgent2 = new AID[result.length];
                                    for (int i = 0; i < result.length; i++) {
                                        collAgent2[i] = result[i].getName();
                                        //System.out.println(result[i].getName().getLocalName());
                                    }
                                }

                            } catch (FIPAException fe) {
                                fe.printStackTrace();
                            }

                            for (int i = 0; i < collAgent2.length; ++i) {
                                try {
                                    ACLMessage confirm = new ACLMessage(ACLMessage.PROPAGATE);
                                    confirm.addReceiver(collAgent2[i]);
                                    confirm.setContent(collAgent2[i].getLocalName());
                                    myAgent.send(confirm);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }

                        step++;
                        myAgent.doDelete();
                    }
            }
        }

        public boolean done() {
            return step == 5;
        }
    }

    public class CSVReader extends Behaviour {//read policy dataset
        CSVReader obj = new CSVReader();

        public void action() {
            String csv = "C:/Users/User/Pictures/JADE MCIR/dataset.csv";
            BufferedReader br = null;
            String line = "";
            String csvSplit = ",";
            String[] football = new String[0];
            try {
                br = new BufferedReader(new FileReader(csv));
                String headerLine = br.readLine();
                while ((line = br.readLine()) != null) {
                    football = line.split(csvSplit);
                    System.out.println(football);
                }
            } catch (IOException io) {
                System.out.println(io);
            }

        }

        public boolean done() {
            return true;
        }

    }
}
