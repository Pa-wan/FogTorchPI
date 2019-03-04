package di.unipi.socc.fogtorchpi.deployment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

import di.unipi.socc.fogtorchpi.application.Application;
import di.unipi.socc.fogtorchpi.application.SoftwareComponent;
import di.unipi.socc.fogtorchpi.application.ExactThing;
import di.unipi.socc.fogtorchpi.application.ThingRequirement;
import di.unipi.socc.fogtorchpi.infrastructure.CloudDatacentre;
import di.unipi.socc.fogtorchpi.infrastructure.FogNode;
import di.unipi.socc.fogtorchpi.infrastructure.Infrastructure;
import di.unipi.socc.fogtorchpi.utils.Couple;
import di.unipi.socc.fogtorchpi.infrastructure.ComputationalNode;
import di.unipi.socc.fogtorchpi.utils.QoSProfile;


public class GASearch {

    private Application A;
    private Infrastructure I;
    private HashMap<String, ArrayList<ComputationalNode>> K;
    private HashMap<String, HashSet<String>> businessPolicies;
    //private ArrayList<String> keepLight;
    int steps;
    public ArrayList<Deployment> D;

    //GA Variables
    int numberOfGenerations;// = 10;
    private int populationSize;// = 10;
    private double MutationProbability;// = 0.1;
    private Random rand = new Random(0);

    private double maxCost;
    private double minCost;
    private BufferedWriter bf;
    // END


    public GASearch(Application A, Infrastructure I) { //, Coordinates deploymentLocation
        this.rand=new Random();

        this.A = A;
        this.I = I;
        K = new HashMap<>();
        D = new ArrayList<>();
        businessPolicies = new HashMap<String, HashSet<String>>();
        //keepLight = new ArrayList<>();
        for (SoftwareComponent s : A.S){
            K.put(s.getId(), new ArrayList<>());
        }

        this.numberOfGenerations = numberOfGenerations;
        this.populationSize = populationSize;
        this.MutationProbability = MutationProbability;
        //this.deploymentLocation = deploymentLocation;
    }

    GASearch(Application A, Infrastructure I, HashMap<String, HashSet<String>> businessPolicies,int numberOfGenerations,int populationSize,double MutationProbability,BufferedWriter bf) {


        this.A = A;
        this.I = I;

        this.maxCost = 0.0;
        this.minCost = Double.MAX_VALUE;

        K = new HashMap<>();
        D = new ArrayList<>();
        this.businessPolicies = businessPolicies;
        //keepLight = new ArrayList<>();
        for (SoftwareComponent s: A.S){
            K.put(s.getId(), new ArrayList<>());
        }

        this.numberOfGenerations = numberOfGenerations;
        this.populationSize = populationSize;
        this.MutationProbability = MutationProbability;
        this.bf = bf;
    }

    private boolean findCompatibleNodes() {
        for (SoftwareComponent s : A.S) {
            for (CloudDatacentre n : I.C.values()) {
                if (n.isCompatible(s) && checkThings(s, n)
                        && ((businessPolicies.containsKey(s.getId())
                        && businessPolicies.get(s.getId()).contains(n.getId()))
                        || !businessPolicies.containsKey(s.getId()))) {
                    K.get(s.getId()).add(n);
                }
            }
        }

        for (SoftwareComponent s : A.S) {
            for (FogNode n : I.F.values()) {
                //System.out.println(s.getId() + " " + n.getId() + " "+ checkThings(s,n));
                if (n.isCompatible(s) && checkThings(s, n)
                        && ((businessPolicies.containsKey(s.getId()) && businessPolicies.get(s.getId()).contains(n.getId()))
                        || !businessPolicies.containsKey(s.getId()))) {
                    K.get(s.getId()).add(n);
                }
            }

        }

        for (SoftwareComponent s : A.S) {
            if (K.get(s.getId()).isEmpty())
                return false;
            bestFirst(K.get(s.getId()), s);
        }

        return true;
    }

    //    public ArrayList<Deployment> findDeployments() {
    public Couple findDeployments() {
        Deployment deployment = new Deployment();

        Boolean b = findCompatibleNodes();
        Collections.sort(A.S, (Object o1, Object o2) -> {

            SoftwareComponent s1 = (SoftwareComponent) o1;
            SoftwareComponent s2 = (SoftwareComponent) o2;
            return Integer.compare(K.get(s1.getId()).size(), K.get(s2.getId()).size());
        });

        return geneticSearch(deployment);
    }






    /**
     * It prunes the search space till the first result.
     * Uses the heuristics.
     * @param deployment
     * @return an eligible deployment.
     */
    private Deployment search(Deployment deployment) {
        if (isComplete(deployment)) {
            D.add(deployment);
            //System.out.println(deployment);
            return deployment;
        }
        SoftwareComponent s = selectUndeployedComponent(deployment);
        if (K.get(s.getId()) != null) {
            for (ComputationalNode n : K.get(s.getId())) { // for all nodes compatible with s
                //System.out.println(steps + " Checking " + s.getId() + " onto node " + n.getId());
                if (isValid(deployment, s, n)) {
                    //System.out.println(steps + " Deploying " + s.getId() + " onto node " + n.getId());
                    deploy(deployment, s, n);
                    Deployment result = search(deployment);
                    if (result != null) {
                        return deployment;
                    }
                    undeploy(deployment, s, n);
                }
                //System.out.println(steps + " Undeploying " + s.getId() + " from node " + n.getId());
            }
        }
        return null;
    }
    /**
     * It returns all existing eligible deployment plans.
     * Results are sorted according to the heuristics (no pruning).
     * @param deployment
     */
    private void exhaustiveSearch(Deployment deployment) {
        if (isComplete(deployment)) {
            D.add((Deployment) deployment.clone());
            //System.out.println(deployment);
            return;
        }
        SoftwareComponent s = selectUndeployedComponent(deployment);
        //System.out.println("Selected :" + s);
        ArrayList<ComputationalNode> Ks = bestFirst(K.get(s.getId()),s);
        //System.out.println(K.get(s.getId()));
        for (ComputationalNode n : Ks) { // for all nodes compatible with s
            steps++;
            //System.out.println(steps + " Checking " + s.getId() + " onto " + n.getId());
            if (isValid(deployment, s, n)) {
                //System.out.println(steps + " Deploying " + s.getId() + " onto " + n.getId());
                deploy(deployment, s, n);
                exhaustiveSearch(deployment);
                undeploy(deployment, s, n);
            }
            //System.out.println(steps + " Undeploying " + s.getId() + " from " + n.getId());
        }

    }

    /**
     * It searches a random solution .

     * @param deployment
     * @return an eligible deployment.
     */
    private void oneRandomSearch(Deployment deployment) {
        if (isComplete(deployment)) {
            D.add((Deployment) deployment.clone());
            return; //ONLY ONE
        }

        SoftwareComponent s = selectUndeployedComponent(deployment);
        List<ComputationalNode> shuffleNodes = K.get(s.getId());

        Collections.shuffle(shuffleNodes);

        for (ComputationalNode n : shuffleNodes) { // for all nodes compatible with s
            if (isValid(deployment, s, n)) {
                deploy(deployment, s, n);
                oneRandomSearch(deployment);
                undeploy(deployment, s, n);
                return; //ONLY ONE
            }
        }

    }


    //start  GA modifications
    private void exit(){
        System.exit(-1);
    }
    private void print(Object s){
        System.out.println(""+s);
    }




    // function to sort hashmap by values
    public static HashMap<Integer, Double> sortByValue(HashMap<Integer, Double> hm,int limit)
    {
        // Create a list from elements of HashMap
        List<Map.Entry<Integer, Double> > list =
                new LinkedList<Map.Entry<Integer, Double> >(hm.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<Integer, Double> >() {
            public int compare(Map.Entry<Integer, Double> o1,
                               Map.Entry<Integer, Double> o2)
            {
                return (o1.getValue()).compareTo(o2.getValue());
//                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        // put data from sorted list to hashmap
        HashMap<Integer, Double> temp = new LinkedHashMap<Integer, Double>();
        int counter =0;
        for (Map.Entry<Integer, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
            counter++;
            if (counter==limit) {
                break;
            }
        }
        return temp;
    }


    //    private ArrayList<Deployment> geneticSearch(Deployment deployment){
    private Couple geneticSearch(Deployment deployment){

        ArrayList<Deployment> Population = new ArrayList<>();
        HashMap<Integer,Double> PopulationFitness = new HashMap<>();

        ArrayList<Deployment> offspring = new ArrayList<>();
        HashMap<Integer,Double> offspringFitness = new HashMap<>();

        int idDep = 0;
        print("Population.size "+Population + " Total de: "+populationSize);
        while (Population.size()<populationSize){ //WARNING at least one deployment must be valid
            oneRandomSearch(deployment);

            if (D.size()==0) continue; //return new Couple(new ArrayList<>(),new ArrayList<>());
            Deployment one = (Deployment) D.get(0).clone();
            print(one);
            Population.add(one);
            //PopulationFitness.put(idDep,computeFitness(one));
            idDep++;
            D.clear();
        }

        for (Deployment d : Population){
            setMaxMINCOSTFitness(d);
        }

        idDep = 0;
        for (Deployment d : Population){
            PopulationFitness.put(idDep,computeFitness(d));
            idDep++;
        }



        double minFit = Double.MAX_VALUE;
        double maxFit = Double.MIN_VALUE;
        for (Deployment d : Population) {
            double fit = computeFitness(d);
            if (minFit > fit) {
                minFit = fit;
            }
            if (maxFit< fit){
                maxFit = fit;
            }
        }
        try {
            bf.write("-1,"+minFit+","+maxFit+"\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }



//        PopulationFitness.put(idDep,computeFitness(one));

        print("INIT");
//        print(Population.size());
//        int e=0;
//        for (Deployment d : Population){
//            print(e+") "+d+" - f:"+PopulationFitness.get(e));
//            e++;
//        }
//        print("SIZE PF  "+PopulationFitness.keySet().size());



        for (int i = 0; i< numberOfGenerations; i++){

            for (Deployment d : Population){
                setMaxMINCOSTFitness(d);
            }


            int validDep = 0;
            for (int j=0;j<populationSize;) { //J is incremented when the crossover of parents is ok
                //Random selection of two fathers in proportion to the fitness values

                Deployment parent1 = tournamentSelection(Population,PopulationFitness,populationSize/10);
                Deployment parent2 = tournamentSelection(Population,PopulationFitness,populationSize/10);

                ArrayList<Deployment> kids = crossover(parent1,parent2);
                boolean bothkids=true;
                for (Deployment child:kids) {
                    bothkids =  (child != null && bothkids);
                }
                if (bothkids){
                   for (Deployment child:kids) {
                        if (rand.nextDouble() <= MutationProbability) {
                            child = mutation(child); //Mutations are always != null
                        }
                        double fitnessChild = computeFitness(child);
                        offspring.add(child);
                        offspringFitness.put(validDep+populationSize, fitnessChild); //WARNING  KEY: j+population
                        validDep++;
                        j++;
//                        print("CROSSOVING "+j);
                    }
                }
            }


//            print("OffSpring Generation");
//            print(offspring.size());
//            int e=0;
//            for (Deployment d : offspring){
//                print(e+") "+d+" - f:"+offspringFitness.get(e+populationSize));
//                e++;
//            }
//            exit();


            //Join both PopulationFitness and offspringFitness and get the best fitness (populationSize)
            HashMap<Integer,Double> bothPopulations = new HashMap<>();
            bothPopulations.putAll(PopulationFitness);
            bothPopulations.putAll(offspringFitness); //KEYS DIFFERENTs

            //DEBUG OK
//            for (Integer bi: bothPopulations.keySet()){
//                print(bi);
//                if (bi<populationSize){
//                    print(Population.get(bi) + " fit: "+PopulationFitness.get(bi) + " fitC: "+computeFitness(Population.get(bi)));
//                }else{
//                    print(offspring.get(bi%populationSize)  + " fit: "+offspringFitness.get(bi) + " fitC: "+computeFitness(offspring.get(bi%populationSize)));
//                }
//            }

            Map<Integer, Double> sortedPopulation = sortByValue(bothPopulations,populationSize); // INFO: from lowest to highest
            //OK

            //creating the new generation
            ArrayList<Integer> idPopulation = new ArrayList(sortedPopulation.keySet());

            PopulationFitness = new HashMap<>();

            //create populationfitness from previous values
            ArrayList<Deployment> NewPopulation = new ArrayList<>();
            for (int j=0;j<populationSize;j++){
                int indexDep  = idPopulation.get(j);

                Deployment d;
                //DEPLOYMENT
                if (indexDep<populationSize){
                    d = Population.get(indexDep);
                }else{
                    d = offspring.get(indexDep%populationSize); //are the same but more clearly
                }
                NewPopulation.add(d);
                PopulationFitness.put(j,bothPopulations.get(indexDep));
            }

            Population = NewPopulation;

            // TEST
//            print("NEW GENERATION: "+i);
//            print(Population.size());
//            int eee=0;
//            for (Deployment d : Population){
//                print(eee+") "+d+" - f:"+PopulationFitness.get(eee));
//                eee++;
//            }
//            exit();
            //OK



            minFit = Double.MAX_VALUE;
            maxFit = Double.MIN_VALUE;
            for (Deployment d : Population) {
                double fit = computeFitness(d);
                if (minFit > fit) {
                    minFit = fit;
                }
                if (maxFit< fit){
                    maxFit = fit;
                }
            }

            try {
                bf.write(i+","+minFit+","+maxFit+"\n");
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        }

//        print("END GENERATIONs ");
//
//        try {
//            bf.flush();
//            bf.close();
//        } catch (IOException e1) {
//            e1.printStackTrace();
//        }
//        exit();


        //Recomputing fitness for tracing purpose
        HashMap<String,Double> fitness = new HashMap<>();
        for (Deployment d : Population){
            double fit = computeFitness(d);
            fitness.put(d.toString(),fit);
//            print("Dep: "+d+"  "+ fitness.get(d.toString()));
        }
        //exit();
        return new Couple(Population,fitness);

    }

    private double computeFitnessResourceConsumption(Deployment deployment){
        List<String> listNodes = new ArrayList<>();
        for (SoftwareComponent s: deployment.getSW()) {
            ComputationalNode n = deployment.getCN(s);
            listNodes.add(n.getId());
        }

        Couple<Double, Double> c = I.consumedResources(deployment,listNodes);
        return (c.getA() + c.getB())/2;

    }

    //TODO doublecheck MAXCost and mincost can be global variables? Same max-min cost for the whole project?
    public void setMaxMINCOSTFitness(Deployment deployment){
        double deploymentCost = deployment.deploymentMonthlyCost.getCost();
        if (deploymentCost>this.maxCost){
            this.maxCost=deploymentCost;
        }
        if (deploymentCost<this.minCost){
            this.minCost=deploymentCost;
        }
    }

    public double computeFitness(Deployment deployment){
        double network = computeFitnessNetwork(deployment);
//        print("\t network "+network);
        double resourceUsage =  computeFitnessResourceConsumption(deployment);
//        print("\t resourceUsage "+resourceUsage);
        double deploymentCost = deployment.deploymentMonthlyCost.getCost();
//        print("\t deploymentCost "+deploymentCost);
        double cost = (deploymentCost - this.minCost)/(this.maxCost-this.minCost);
//        print("\t cost "+cost);
        double fitness = (1.0/3.0)*(cost + resourceUsage + network);
//        print("\tCF> Deployment "+ deployment+"\tFITNESS "+ fitness);
        return fitness;
    }


    /*
        father1:
        a1 - c1 <- Cut = 1
        b1 - c2

        father2:
        a2 - c3
        b2 - c4

        child1:
        ++++++++
        a1 - c1
        b1 - c4

        child2:
        ++++++++
        a2 - c3
        b2 - c2

        Firstly, we deploy and secondly, we undeploy to clean all internal structures
     */
    private ArrayList<Deployment> crossover(Deployment parent1, Deployment parent2) {
        ArrayList<Deployment> list = new ArrayList<>();

        Deployment child = new Deployment();
        Deployment childOk = new Deployment();

        Deployment child2 = new Deployment();
        Deployment childOk2 = new Deployment();


        SoftwareComponent sFailure = null;
        int point = 0; //.... no idea to do better with JAVA...

        ArrayList<SoftwareComponent> lf1 = new ArrayList<>(parent1.getSW());
        ArrayList<SoftwareComponent> lf2 = new ArrayList<>(parent2.getSW());

        int rndValue = rand.nextInt((lf1.size()));

        boolean notValid = false;
        for (SoftwareComponent s : lf1) {
            if (point < rndValue) {
                // The service of father1 links with the CN of father2
                ComputationalNode n = parent1.getCN(s);
                if (isValid(child, s, n)) {
                    fictionalDeploy(child, s, n);
                } else {
                    notValid = true;
                    break;
                }
            } else {
                SoftwareComponent s2 = lf1.get(point);
                ComputationalNode n = parent2.getCN(s2);
                try {
                    if (isValid(child, s2, n)) {
                        fictionalDeploy(child, s2, n);
                    } else {
                        notValid = true;
                        break;
                    }
                } catch (NullPointerException e) {
                    notValid = true;
                    break;
                }
            }
            point++;
        }
        childOk = (Deployment) child.clone();

        point = 0;
        for (SoftwareComponent s : lf2) {
            if (point < rndValue) {
                // The service of father1 links with the CN of father2
                ComputationalNode n = parent2.getCN(s);
                if (isValid(child2, s, n)) {
                    fictionalDeploy(child2, s, n);
                } else {
                    notValid = true;
                    break;
                }

            } else {
                SoftwareComponent s2 = lf2.get(point);
                ComputationalNode n = parent1.getCN(s2);
                try {
                    if (isValid(child2, s2, n)) {
                        fictionalDeploy(child2, s2, n);
                    } else {
                        notValid = true;
                        break;

                    }
                } catch (NullPointerException e) {
                    notValid = true;
                    break;
                }
            }
            point++;
        }
        childOk2 = (Deployment) child2.clone();

        if (!notValid) {
            list.add(childOk);
            list.add(childOk2);
        } else {
            list.add(null);
            list.add(null);
        }

//        print("FAHTER 1:");
//        print(father1);
//        print("-------");
//        print("Father 2:");
//        print(father2);
//
//        print("C1----");
//        print(childOk);
//
//        print("C2----");
//        print(childOk2);
//        exit();


        return list;

    }

    /*
        The mutation consist on to change a random SW in a random CN
        ai - Cj
        --------
        ai - Ck (where j!=k)
     */
    private Deployment mutation(Deployment dep) {
        //ELEMENT TO BE CHANGED
        ArrayList<SoftwareComponent> lf1 = new ArrayList<>(dep.getSW());
        int rndS =  rand.nextInt((lf1.size()));
        SoftwareComponent s = lf1.get(rndS);

        ComputationalNode cn1 = dep.getCN(s);
//        print("Componente SW "+s.getId());
//        print("Componente CN "+cn1.getId());

        //ELEMENT TO PUT
        //Best solution (for me) to find all the elements of infrastructure
        int sizeFC = I.F.size()+I.C.size();

        //OK
        ComputationalNode n = null;
        do { //We guarantee a change of CN (no efficient)
            int rndI = rand.nextInt(sizeFC);
            int counter = 0;

            if (rndI < I.F.size()) {
                for (String key : I.F.keySet()) {
                    if (counter == rndI) {
                        n = I.F.get(key);
                        break;
                    }
                    counter++;
                }
            } else {
                rndI = rndI - I.F.size(); //normalize the value index
                for (String key : I.C.keySet()) {
                    if (counter == rndI) {
                        n = I.C.get(key);
                        break;
                    }
                    counter++;
                }
            }
        }while(n==cn1);

        //DOING THE CHANGE
        Deployment themutation = null;
        undeploy(dep,s,cn1);

        if (isValid(dep,s,n)) {
            deploy(dep, s, n);
            themutation = (Deployment) dep.clone();
            undeploy(dep, s, n);
        }
        deploy(dep,s,cn1);

        if (themutation==null) {
            return dep;
        }else{
            return themutation;
        }
    }

    private Deployment tournamentSelection( ArrayList<Deployment>  population,
                                            HashMap<Integer,Double> PopulationFitness,
                                            int numberOfSelections){

        Deployment currentFather = null;
        Double currentFatherFitness = Double.MAX_VALUE;

        for (int i =0;i<numberOfSelections;i++){
            int rndValue =  rand.nextInt((population.size()));

            if (PopulationFitness.get(rndValue) < currentFatherFitness){
                currentFather = population.get(rndValue);
                currentFatherFitness =PopulationFitness.get(rndValue);
            }
        }
        return currentFather;

    }



    private ArrayList<ComputationalNode> bestFirst(ArrayList<ComputationalNode> Ks, SoftwareComponent s) {
        for (ComputationalNode n : Ks) {
            n.computeHeuristic(s);//, this.deploymentLocation);
        }
        Collections.sort(Ks);
        return Ks;
    }

    private boolean checkLinks(Deployment deployment, SoftwareComponent s, ComputationalNode n) {
        for (SoftwareComponent c : deployment.keySet()) {
            ComputationalNode m = deployment.get(c); // nodo deployment c
            Couple couple1 = new Couple(c.getId(), s.getId());
            Couple couple2 = new Couple(s.getId(), c.getId());
            if (A.L.containsKey(couple1)) {
                QoSProfile req1 = A.L.get(couple1);
                QoSProfile req2 = A.L.get(couple2);
                Couple c1 = new Couple(m.getId(), n.getId());
                Couple c2 = new Couple(n.getId(), m.getId());
                //System.out.println("Finding a link for " + couple1 + " between " + c1);
                if (I.L.containsKey(c1)) {
                    QoSProfile off1 = I.L.get(c1);
                    QoSProfile off2 = I.L.get(c2);

                    if (!off1.supports(req1) || !off2.supports(req2)) {
                        return false;
                    }
                } else {
                    //System.out.println("It does not exist");
                    return false;
                }
            }
        }
        return true;
    }

    private void deployLinks(Deployment deployment, SoftwareComponent s, ComputationalNode n) {
        for (SoftwareComponent c : deployment.keySet()) {
            ComputationalNode m = deployment.get(c);
            Couple couple1 = new Couple(c.getId(), s.getId());
            Couple couple2 = new Couple(s.getId(), c.getId());

            if (A.L.containsKey(couple1) && A.L.containsKey(couple2)) {
                QoSProfile req1 = A.L.get(couple1); //c,s
                QoSProfile req2 = A.L.get(couple2); //s,c
                Couple c1 = new Couple(m.getId(), n.getId()); // m,n
                Couple c2 = new Couple(n.getId(), m.getId()); // n,m
                if (I.L.containsKey(c1)) {
                    QoSProfile pl1 = I.L.get(c1);
                    QoSProfile pl2 = I.L.get(c2);
                    pl1.setBandwidth(pl1.getBandwidth() - req1.getBandwidth());
                    pl2.setBandwidth(pl2.getBandwidth() - req2.getBandwidth());
                }
            }
        }

        for (ThingRequirement t : s.Theta) {
            ExactThing e = (ExactThing) t;
            if (n.isReachable(e.getId(), I, e.getQNodeThing(), e.getQThingNode())) {
                Couple c1 = new Couple(n.getId(), e.getId()); //c1 nodeThing

                QoSProfile pl1 = I.L.get(c1);
                QoSProfile pl2 = I.L.get(new Couple(e.getId(), n.getId()));

                pl1.setBandwidth(pl1.getBandwidth() - e.getQNodeThing().getBandwidth());
                pl2.setBandwidth(pl2.getBandwidth() - e.getQThingNode().getBandwidth());

            }
        }
    }

    private boolean checkThings(SoftwareComponent s, ComputationalNode n) {
        //System.out.println("Checking things for "+ s.getId() + " -- " + n);
        for (ThingRequirement r : s.getThingsRequirements()) {
            //System.out.println(s.getId() + " " + r.toString());
            if (r.getClass() == ExactThing.class) {
                ExactThing e = (ExactThing) r;
                if (!n.isReachable(e.getId(), I, e.getQNodeThing(), e.getQThingNode())) { // directly or remotely
                    //System.out.println("Not reachable "+e.getId() +" from " + n.getId());
                    return false;
                }
            }
        }

        return true;
    }

    private void undeployLinks(Deployment deployment, SoftwareComponent s, ComputationalNode n) {
        for (SoftwareComponent c : deployment.keySet()) {
            ComputationalNode m = deployment.get(c);
            Couple couple1 = new Couple(c.getId(), s.getId());
            Couple couple2 = new Couple(s.getId(), c.getId());

            if (A.L.containsKey(couple1) && A.L.containsKey(couple2)) {
                QoSProfile al1 = A.L.get(couple1);
                QoSProfile al2 = A.L.get(couple2);
                Couple c1 = new Couple(m.getId(), n.getId());
                Couple c2 = new Couple(n.getId(), m.getId());
                if (I.L.containsKey(c1)) {
                    QoSProfile pl1 = I.L.get(c1);
                    QoSProfile pl2 = I.L.get(c2);

                    pl1.setBandwidth(pl1.getBandwidth() + al1.getBandwidth());
                    pl2.setBandwidth(pl2.getBandwidth() + al2.getBandwidth());
                }
            }

        }

        for (ThingRequirement t : s.Theta) {
            ExactThing e = (ExactThing) t;
            //System.out.println("Request" + e);
            if (n.isReachable(e.getId(), I, e.getQNodeThing(), e.getQThingNode())) {
                Couple c1 = new Couple(n.getId(), e.getId());

                QoSProfile pl1 = I.L.get(c1);
                QoSProfile pl2 = I.L.get(new Couple(e.getId(), n.getId()));

                pl1.setBandwidth(pl1.getBandwidth() + e.getQNodeThing().getBandwidth());
                pl2.setBandwidth(pl2.getBandwidth() + e.getQThingNode().getBandwidth());

            }
        }
    }

    private boolean isValid(Deployment deployment, SoftwareComponent s, ComputationalNode n) throws NullPointerException{
//        System.out.println(n.getId() + " is Compatible " + n.isCompatible(s)  );
//        System.out.println(n.getId() + " links " + checkLinks(deployment, s, n)  );
//        System.out.println(n.getId() + " things " + checkThings(s, n));
        return n.isCompatible(s) && checkLinks(deployment, s, n) && checkThings(s, n);
    }

    private void deploy(Deployment deployment, SoftwareComponent s, ComputationalNode n) {
        deployment.put(s, n);
        deployment.addCost(s,n, I);
        //System.out.println(deployment + " " + deployment.size());
        n.deploy(s);
        deployLinks(deployment, s, n);
    }
    private void fictionalDeploy(Deployment deployment, SoftwareComponent s, ComputationalNode n) {
        deployment.put(s, n);
        deployment.addCost(s,n, I);
        //System.out.println(deployment + " " + deployment.size());
//        n.deploy(s);
//        deployLinks(deployment, s, n);
    }



    private void undeploy(Deployment deployment, SoftwareComponent s, ComputationalNode n) {
        if (deployment.containsKey(s)) {
            deployment.remove(s);
            deployment.removeCost(s, n, I);
            n.undeploy(s);
            undeployLinks(deployment, s, n);
        }
//        System.out.println("UNDEP"+deployment);
    }

    private SoftwareComponent selectUndeployedComponent(Deployment deployment) {
        //System.out.println(deployment.size());
        return A.S.get(deployment.size());
    }

    private boolean isComplete(Deployment deployment) {
        return deployment.size() == A.S.size();
    }

    public void addBusinessPolicies(String component, List<String> allowedNodes) {
        HashSet<String> policies = new HashSet<>();
        policies.addAll(allowedNodes);
        this.businessPolicies.put(component, policies);
    }

    public void addKeepLightNodes(List<String> keepLightNodes) {
        for (String n : keepLightNodes){
            if (I.C.containsKey(n)){
                I.C.get(n).setKeepLight(true);
            }
            if(I.F.containsKey(n)){
                I.F.get(n).setKeepLight(true);
            }

        }
    }

/*    public void executeDeployment(Deployment d){

        Deployment dep = new Deployment();
        dep.deploymentMonthlyCost = d.deploymentMonthlyCost;

        for (SoftwareComponent component : d.keySet()){
            ComputationalNode n = d.get(component);
            dep.put(component, n);
            n.deploy(component);
            deployLinks(dep, component, n);
        }

    }

*/

    //
    public boolean validDeployment(Deployment d){
        for (SoftwareComponent component : d.keySet()){
            ComputationalNode n = d.get(component);
            if (! isValid(d,component,n)){
                return false;
            }
        }
        return true;
    }

    public void removeDeployment(Deployment d){

        for (SoftwareComponent component : d.keySet()){
            ComputationalNode n = d.get(component);
            n.undeploy(component);
            undeployLinks(d, component, n);
        }

    }

    public void executeDeployment(Deployment d){

        for (SoftwareComponent component : d.keySet()){
            ComputationalNode n = d.get(component);
            // dep.put(component, n);
            n.deploy(component);
            deployLinks(d, component, n);
        }

    }

    private double computeFitnessNetwork(Deployment deployment) {

        double totalLinks = I.L.size() - I.C.size() - I.F.size();
        double thingLinks = 2.0 * I.T.size() * (I.C.size() + I.F.size());
        totalLinks -= thingLinks ; //total links excluding self-links

//        print(deployment);

        double bandwidthDifference = 0.0;
        double latencyDifference = 0.0;

        // this contains a reference to the usedLinks just to count them for considering free links in the end
        HashSet<Couple> usedLinks = new HashSet<>();

        // this counts the interactions for which we are trying to reduce latency
        double interactions = 0;

        for (SoftwareComponent c : deployment.keySet()) {
            for (SoftwareComponent s : deployment.keySet()){
                if (!c.getId().equals(s.getId())){  // c != s

                    ComputationalNode m = deployment.get(c);
                    ComputationalNode n = deployment.get(s);
                    Couple couple1 = new Couple(c.getId(), s.getId());

                    if (!m.getId().equals(n.getId())){ // on the same node is meaningless

                        if (A.L.containsKey(couple1)) {
                            QoSProfile req1 = A.L.get(couple1); // requirements c,s
                            Couple c1 = new Couple(m.getId(), n.getId());
                            usedLinks.add(c1);

                            if (I.L.containsKey(c1)) { // the E2E link exists

                                QoSProfile pl1 = I.L.get(c1); // QoS m,n

//                                print("Computing % between " + c1 + " for " + couple1);
                                double percentageBW1 = (pl1.getBandwidth() - req1.getBandwidth()) // m,n
                                        / pl1.getBandwidth();


                                double percentageLat = (((double) req1.getLatency()) - ((double) pl1.getLatency())) // m,n
                                        / ((double) req1.getLatency());

//                                print("Percent Latency " + percentageLat);

                                bandwidthDifference += percentageBW1; //+ percentageBW2;
                                latencyDifference += percentageLat;

                                interactions++;
                            }
                        }
                    }
                }
            }
        }

        for (SoftwareComponent s : deployment.keySet()) {
            for (ThingRequirement t : s.Theta) {

                ExactThing e = (ExactThing) t;
                ComputationalNode n = deployment.get(s); // the node trying to access the thing
                String thingNode = null;

                for (FogNode f : I.F.values()) {
                    if (f.connectsToThing(e.getId())){
                        thingNode = f.getId();              // the node to which the thing connects
                    }
                }

                if (!thingNode.equals(n.getId())){ // if n reaches t remotely

                    Couple c1 = new Couple(n.getId(), thingNode); //c1 nodeThing
                    Couple c2 = new Couple(thingNode, n.getId());

//                    print(c1);
//                    print(c2);

                    usedLinks.add(c1);
                    usedLinks.add(c2);

                    QoSProfile pl1 = I.L.get(c1);
                    QoSProfile pl2 = I.L.get(c2);

                    double percentageBW1 = (pl1.getBandwidth() - e.getQNodeThing().getBandwidth()) // node-thing
                            / pl1.getBandwidth();
                    double percentageBW2 = (pl2.getBandwidth() - e.getQThingNode().getBandwidth()) // thing-node
                            / pl2.getBandwidth();

                    double percentageLat = (((double) e.getQNodeThing().getLatency()) - ((double) pl1.getLatency())) // m,n
                            / ((double) e.getQNodeThing().getLatency()) ;

                    bandwidthDifference = bandwidthDifference + percentageBW1 + percentageBW2;

                    latencyDifference += percentageLat;
                    interactions++;

                }
            }
        }

//        print(usedLinks);

        double unusedLinks = totalLinks - usedLinks.size();
        bandwidthDifference += unusedLinks;

//        print("bandwidthDifference "+bandwidthDifference);
//        print("totalLinks "+totalLinks);


        double averageBandwidthDifference = bandwidthDifference / totalLinks;
        double averageLatencyDifference = interactions == 0 ? 1.0 : latencyDifference / interactions;

//        print("bw difference " + averageBandwidthDifference);
//        print("lat difference " + averageLatencyDifference);

        return 0.5 * (averageBandwidthDifference + averageLatencyDifference);

    }

}