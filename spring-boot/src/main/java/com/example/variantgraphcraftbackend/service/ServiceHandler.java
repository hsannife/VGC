package com.example.variantgraphcraftbackend.service;

import com.example.variantgraphcraftbackend.model.*;
import com.example.variantgraphcraftbackend.model.filemanager.IndexReader;
import com.example.variantgraphcraftbackend.model.filemanager.InfoReader;
import com.example.variantgraphcraftbackend.model.filemanager.PhenotypeReader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class ServiceHandler {

    private VCFParser vcfParser;
    private GenelistParser geneParser;
    private HashMap<UploadedFile, InfoReader> infoMap;
    private HashMap<UploadedFile, IndexReader> indexMap;
    private HashMap<UploadedFile, PhenotypeReader> phenotypeMap;
    private HashMap<String, UploadedFile> pathMap;
    private UploadedFile currFile;
    private ZoomController zoomController;
    private ClinvarParser clinvarParser;

    /**
     * Constructor of ServiceHandler. Note that this class is created BEFORE the
     * BarController and FileController.
     * @throws IOException
     */
    public ServiceHandler() throws IOException {
//        System.out.println("Wow! A ServiceHandler is created...");
        this.currFile = null;
        this.infoMap = new HashMap<UploadedFile, InfoReader>();
        this.indexMap = new HashMap<UploadedFile, IndexReader>();
        this.phenotypeMap = new HashMap<UploadedFile, PhenotypeReader>();
        this.pathMap = new HashMap<String, UploadedFile>();
        this.vcfParser = new VCFParser(this.infoMap, this.indexMap);
        this.geneParser = new GenelistParser("genelist_ensemble_updated.txt");
        this.zoomController = new ZoomController();
        this.clinvarParser = new ClinvarParser();
    }

    /**
     * Generates aboutBox information.
     * @return
     */
    public AboutBox generateAbout() {
        AboutBox about = new AboutBox(this.currFile.getPath(),
                this.infoMap.get(this.currFile).getVersion(),
                this.infoMap.get(this.currFile).getPatients(),
                this.infoMap.get(this.currFile).getNumChrom());
//        System.out.println("REQUEST LOGIC DONE");
        return about;
    }

    public void handleSelected(UploadedFile file) throws IOException {
        this.currFile = file;
        this.pathMap.put(file.getPath(), file);
        this.vcfParser.processSelectedFile(file);
    }

    public void selectByPath(String vcfPath, String phenotypePath, boolean updatePhenotype) throws IOException {
        this.currFile = this.pathMap.get(vcfPath);
//        System.out.println("In selectByPath. Current file: " + this.currFile);
        this.vcfParser.processSelectedFile(this.currFile);
        if (phenotypePath == null && this.phenotypeMap.containsKey(this.currFile)) {
            this.phenotypeMap.remove(this.currFile);
        }
        if (updatePhenotype) {
            PhenotypeReader phenotypeReader = new PhenotypeReader(phenotypePath);
            phenotypeReader.readPhenotype();
            this.phenotypeMap.put(this.currFile, phenotypeReader);
        }
    }

    public ArrayList<String> getFileHeader() {
        String[] fullHeader = this.infoMap.get(this.currFile).getFullHeader().split("\t");
        return new ArrayList<>(Arrays.asList(fullHeader));
    }

    //BAR_GRAPH RELATED METHODS -------------------------------------------------------------------------
    public BarView displayBarView(String passFilter) {
//        System.out.println("Displaying BarGraph from serviceHandler.");
        BarView chromToVar = new BarView("Variants per Chromosome", "Chromosome", "Variants", null, null);
//        System.out.println("Current file: ");
//        System.out.println(this.currFile.getPath());
//        System.out.println("All files: ");
        if (passFilter.equals("PATHOGENIC")) {
            chromToVar.populateGraph(this.indexMap.get(this.currFile).getPathMap(), this.infoMap.get(this.currFile).getChromosomes());
        } else if (passFilter.equals("PASS")) {
            chromToVar.populateGraph(this.indexMap.get(this.currFile).getPassMap(), this.infoMap.get(this.currFile).getChromosomes());
        } else {
            chromToVar.populateGraph(this.indexMap.get(this.currFile).getChromMap(), this.infoMap.get(this.currFile).getChromosomes());
        }
//        System.out.println("REQUEST LOGIC DONE");
        return chromToVar;
    }

    public BarView displayZoomedGraph(String chr, String passFilter) throws IOException {
//        System.out.println("In displayZoomedGraph method of ServiceHandler. Current file is: " + this.currFile.getPath());
        if (!this.zoomController.isRead()) {
            this.zoomController.readBPInfo();
//            System.out.println("ZoomController done reading. Chr is: " + chr);
        }
        HashMap<Integer, Integer> template = this.zoomController.generateDataTemplate(chr);
        this.vcfParser.getChromHistrogramData(chr, 1000000, template, this.getChrStart(chr), this.getChrEnd(chr), passFilter, this.currFile.getPath());
        BarView zoomedGraph = new BarView("Variants Found by Position on Chromosome " + chr +
                ", Range: ALL" + ", FILTER: " + passFilter, "Position",
                "Number of Variants", "1000000", chr);
        zoomedGraph.populateZoomedGraph(template, new HashMap<Integer, ArrayList<SubBar>>());
//        System.out.println("REQUEST LOGIC DONE");
        return zoomedGraph;
    }

    public BarView displayFurtherZoom(String chr, int start, int end, int zoomFactor, String passFilter) throws IOException {
//        System.out.println("In displayFurtherZoom method of ServiceHandler. Current file is: " + this.currFile.getPath());
        HashMap<Integer, Integer> template = new HashMap<Integer, Integer>();
//        HashMap<Integer, ArrayList<Integer>> posMap = new HashMap<Integer, ArrayList<Integer>>();
        HashMap<Integer, ArrayList<String[]>> posMap = new HashMap<Integer, ArrayList<String[]>>();
        int increment = this.zoomController.generateZoomedTemplate(template, posMap, start, end, zoomFactor);
//        System.out.println("Template retrieved.");
        this.vcfParser.getChromHistrogramData(chr, increment, template, posMap, this.getChrStart(chr), this.getChrEnd(chr), start, end, passFilter, this.currFile.getPath());
//        System.out.println("All variant positions: " + posMap);
        HashMap<Integer, ArrayList<SubBar>> subBarMap = new HashMap<Integer, ArrayList<SubBar>>();
        if (increment <= 10000) {
            subBarMap = this.geneParser.getGeneInfoForVariants(posMap, chr);
            this.clinvarParser.populateVariantInfo(subBarMap, chr);
        }
        BarView zoomedGraph = new BarView("Variants Found by Position on Chromosome " + chr +
                ", Range: " + start + "-" + end + ", FILTER: " + passFilter,
                "Position", "Number of Variants", String.valueOf(increment), chr);
        zoomedGraph.populateZoomedGraph(template, subBarMap);
        zoomedGraph.setSvpData(posMap, this.getFileHeader());
//        System.out.println("REQUEST LOGIC DONE");
        return zoomedGraph;
    }

    public BarView displayGeneHistogram(String gene, String passFilter) throws IOException, NullPointerException {
//        System.out.println("In displayGeneView method of ServiceHander. Current file is: " + this.currFile.getPath() + " and gene: " + gene);
        String[] geneInfo = this.geneParser.getGeneLocation(gene);
        if (geneInfo == null) {
//            System.out.println("REQUEST LOGIC DONE");
            return null;
        } else {
//            System.out.println("Gene found on chr: " + geneInfo[0] + ", StartPos: " + geneInfo[1] + ", endPos: " + geneInfo[2]);
            Integer range = Integer.valueOf(geneInfo[2]) - Integer.valueOf(geneInfo[1]);
            int zoom = range.toString().length() - 3;
            int factor = (int) Math.pow(10, zoom);
            String chr = geneInfo[0].substring(3);
            HashMap<Integer, Integer> template = new HashMap<Integer, Integer>();
//            HashMap<Integer, ArrayList<Integer>> posMap = new HashMap<Integer, ArrayList<Integer>>();
            HashMap<Integer, ArrayList<String[]>> posMap = new HashMap<Integer, ArrayList<String[]>>();

            int increment = this.zoomController.generateTemplateForGeneGraph(template, posMap, Integer.valueOf(geneInfo[1]),
                    Integer.valueOf(geneInfo[2]), factor);
//            System.out.println("Template retrieved.");
            this.vcfParser.getChromHistrogramData(chr, increment, template, posMap, this.getChrStart(chr), this.getChrEnd(chr),
                    Integer.valueOf(geneInfo[1]), Integer.valueOf(geneInfo[2]),
                    passFilter, this.currFile.getPath());
//            System.out.println("All variant positions: " + posMap);
            HashMap<Integer, ArrayList<SubBar>> subBarMap = new HashMap<Integer, ArrayList<SubBar>>();
            if (increment <= 10000) {
                subBarMap = this.geneParser.getGeneInfoForVariants(posMap, geneInfo[0].substring(3));
                this.clinvarParser.populateVariantInfo(subBarMap, geneInfo[0]);
            }
            BarView zoomedGraph = new BarView("Variants Found on Gene " + gene + " on Chromosome " + chr +
                    ", Range: " + geneInfo[1] + "-" + geneInfo[2] + ", FILTER: " + passFilter,
                    "Position", "Number of Variants", String.valueOf(factor),
                    geneInfo[0].substring(3));
            zoomedGraph.populateZoomedGraph(template, subBarMap);
//            System.out.println("REQUEST LOGIC DONE");
            return zoomedGraph;
        }
    }

    public BarView displayRangeHistogram(String chr, int start, int end, String passFilter) throws IOException {
//        System.out.println("In displayFurtherZoom method of ServiceHandler. Current file is: " + this.currFile.getPath());
        HashMap<Integer, Integer> template = new HashMap<Integer, Integer>();
//        HashMap<Integer, ArrayList<Integer>> posMap = new HashMap<Integer, ArrayList<Integer>>();
        HashMap<Integer, ArrayList<String[]>> posMap = new HashMap<Integer, ArrayList<String[]>>();

        int increment = this.zoomController.generateTemplateForRangeGraph(template, posMap, start, end);
//        System.out.println("Template retrieved.");
        this.vcfParser.getChromHistrogramData(chr, increment, template, posMap, this.getChrStart(chr), this.getChrEnd(chr),
                                                start, end, passFilter, this.currFile.getPath());
//        System.out.println("All variant positions: " + posMap);

        HashMap<Integer, ArrayList<SubBar>> subBarMap = new HashMap<Integer, ArrayList<SubBar>>();
        if (increment <= 10000) {
            subBarMap = this.geneParser.getGeneInfoForVariants(posMap, chr);
            this.clinvarParser.populateVariantInfo(subBarMap, chr);
        }
        BarView zoomedGraph = new BarView("Variants Found by Range on Chromosome " + chr +
                                                ", Range: " + start + "-" + end + ", FILTER: " + passFilter,
                                "Position", "Number of Variants", String.valueOf(increment), chr);
        zoomedGraph.populateZoomedGraph(template, subBarMap);
//        System.out.println("REQUEST LOGIC DONE");
        return zoomedGraph;
    }

//    public SingleVariantPathogenicity getSingleVariantAnalysis(String chr, String pos) {
//        return null;
//    }


    //VARIANT_DATA RELATED METHODS -------------------------------------------------------------------------
    /**
     * Displays table view by range. PASS filter set to ALL --> Table filtering can take place in frontend?
     */
    public GridView displayGridView(String chr, int startPos, int endPos) throws IOException {
//        System.out.println("In displayGridView method of ServiceHander. Current file is: " + this.currFile.getPath());
        List<String[]> varList = this.vcfParser.getLinesByPos(chr, this.getChrStart(chr), this.getChrEnd(chr), startPos, endPos, "ALL", this.currFile.getPath());

        GridView table = new GridView("Table view of variants between " + startPos + " and " + endPos +
                " on chromosome " + chr +  " . To view a different range, enter your query in the top-right searchbar.");
        String[] fullHeader = this.infoMap.get(this.currFile).getFullHeader().split("\t");
        table.setHeader(new ArrayList<>(Arrays.asList(fullHeader)));
        for (String[] row : varList) {
            table.addRow(new ArrayList<>(Arrays.asList(row)));
        }
//        System.out.println("REQUEST LOGIC DONE");
        return table;
    }

    /**
     * Displays table view by gene. Calls method above.
     */
    public GridView displayGeneView(String gene) throws IOException {
//        System.out.println("In displayGeneView method of ServiceHander. Current file is: " + this.currFile.getPath() + " and gene: " + gene);
        String[] geneInfo = this.geneParser.getGeneLocation(gene);
        if (geneInfo == null) {
            return null; //CHANGE TO EXCEPTION?
        } else {
//            System.out.println("Gene found on chr: " + geneInfo[0] + ", StartPos: " + geneInfo[1] + ", endPos: " + geneInfo[2]);
            GridView table = this.displayGridView(geneInfo[0].substring(3), Integer.valueOf(geneInfo[1]), Integer.valueOf(geneInfo[2]));
            table.setQueryName("Table view of variants on gene " + gene + " on chromosome " + geneInfo[0] + ", between " +
                    geneInfo[1] + " and " + geneInfo[2] + ". To view a different gene, enter your query in the top-right searchbar.");
            table.setId((long)1);
            return table;
        }
    }

    /**
     * Returns single VCF line as arrayList.
     */
    public ArrayList<String> getLineForPos(String chr, int startPos, int endPos) throws IOException, IndexOutOfBoundsException {
//        System.out.println("In displayGeneView method of ServiceHander. Current file is: " + this.currFile.getPath());
        List<String[]> varList = this.vcfParser.getLinesByPos(chr, this.getChrStart(chr), this.getChrEnd(chr), startPos, endPos, "ALL", this.currFile.getPath());
        return new ArrayList<String>(Arrays.asList(varList.get(0)));
    }

    //NODE_GRAPH RELATED METHODS -------------------------------------------------------------------------
    public NodeView displayGraphByGene(String gene, String passFilter, boolean HR, boolean HT, boolean HA) throws IOException {
        String[] geneInfo = this.geneParser.getGeneLocation(gene);
        if (geneInfo == null) {
//            System.out.println("REQUEST LOGIC DONE");
            return null;
        } else {
            String chr = geneInfo[0].substring(3);
//            System.out.println("Gene found on chr: " + chr + ", StartPos: " + geneInfo[1] + ", endPos: " + geneInfo[2]);
            ArrayList<String> info = new ArrayList<String>(Arrays.asList(geneInfo));
            info.add(0, gene);
            NodeView nodeView = new NodeView("Node Graph for Gene " + gene, 1, "gene", "dp");
            nodeView.setGraphInfo(info);
            List<String[]> varList = this.vcfParser.getLinesByPos(chr, this.getChrStart(chr), this.getChrEnd(chr), Integer.valueOf(geneInfo[1]), Integer.valueOf(geneInfo[2]), passFilter, this.currFile.getPath());
            String[] sampleList = this.infoMap.get(this.currFile).getAllPatients().split("\t");
            if (this.phenotypeMap.containsKey(this.currFile)) {
                nodeView.populateSingleGeneGraph(varList, gene, sampleList, this.phenotypeMap.get(this.currFile), HR, HT, HA);
            } else {
                nodeView.populateSingleGeneGraph(varList, gene, sampleList, HR, HT, HA);
            }
//            System.out.println("REQUEST LOGIC DONE");
            return nodeView;
        }
    }

    public NodeView displayGraphByRange(String chr, int start, int end, String passFilter, boolean HR, boolean HT, boolean HA) throws IOException {
        List<String[]> var = this.vcfParser.getLinesByPos(chr, this.getChrStart(chr), this.getChrEnd(chr), start, end, passFilter, this.currFile.getPath());
        HashMap<String, String> varToGeneMap = new HashMap<String, String>();
        HashMap<String, ArrayList<String>> geneToVarMap = new HashMap<String, ArrayList<String>>();
        for (String[] v : var) {
            String[] geneInfo = this.geneParser.findInfoByPos(Integer.valueOf(v[1]), chr);
//            System.out.println("Gene found on " + geneInfo[0] + geneInfo[1] + ", gene: " + geneInfo[2]);
            varToGeneMap.put(v[1], geneInfo[2]);
            if (geneToVarMap.containsKey(geneInfo[2])) {
                geneToVarMap.get(geneInfo[2]).add(v[1]);
            } else {
                geneToVarMap.put(geneInfo[2], new ArrayList<>());
                geneToVarMap.get(geneInfo[2]).add(v[1]);
            }
        }
        if (var.size() > 0) {
            NodeView nodeView = new NodeView("Node Graph for Variant Range " + start + " to " + end, 1, "range", "dp");
            String[] sampleList = this.infoMap.get(this.currFile).getAllPatients().split("\t");
            ArrayList<String> info = new ArrayList<String>(Arrays.asList(new String[] {"0", chr, String.valueOf(start), String.valueOf(end)}));
            nodeView.setGraphInfo(info);
            if (this.phenotypeMap.containsKey(this.currFile)) {
                nodeView.populateMultiGeneGraph(var, varToGeneMap, sampleList, this.phenotypeMap.get(this.currFile), HR, HT, HA);
            } else {
                nodeView.populateMultiGeneGraph(var, varToGeneMap, sampleList, HR, HT, HA);
            }
//            System.out.println("REQUEST LOGIC DONE");
            return nodeView;
        } else {
            NodeView nodeView = new NodeView("Node Graph for Variant Position " + start + " to " + end, 1, "range", "dp");
//            System.out.println("REQUEST LOGIC DONE");
            return nodeView;
        }
    }

    public MapView generateHeatMap(MapState type, String passFilter, ArrayList<String> chr, ArrayList<String> gene,
                                   ArrayList<Integer> start, ArrayList<Integer> end) throws IOException {
//        Map<String, Map<String, Set<String[]>>> helperMap = new HashMap<String, Map<String, Set<String[]>>>();
        Map<String, Map<String, List<String[]>>> helperMap = new HashMap<String, Map<String, List<String[]>>>();
        List<String[]> data = new ArrayList<String[]>();
        String title = null;
        switch (type) {
            case RANGE:
                for (int i = 0; i < chr.size(); i++) {
                    List<String[]> retrievedVariants = this.vcfParser.getLinesByPos(chr.get(i), this.getChrStart(chr.get(i)),this.getChrEnd(chr.get(i)),
                            start.get(i), end.get(i), passFilter, this.currFile.getPath());
                    data.addAll(retrievedVariants);
                    this.geneParser.getRangeToGeneInfo(helperMap, chr.get(i), retrievedVariants);
                }
                title = "HeatMap of Read Depth by Sample, PassFilter: " + passFilter;
                break;
            case GENE:
                for (int i = 0; i < gene.size(); i++) {
                    String[] geneInfo = this.geneParser.getGeneLocation(gene.get(i));
                    if (geneInfo != null) {
//                        System.out.println("Gene found on chr: " + geneInfo[0] + ", StartPos: " + geneInfo[1] + ", endPos: " + geneInfo[2] + ", emsemble ID: " + geneInfo[3]);
                        String retrievedChr = geneInfo[0].substring(3);
                        List<String[]> retrievedVariants = this.vcfParser.getLinesByPos(retrievedChr, this.getChrStart(retrievedChr), this.getChrEnd(retrievedChr),
                                Integer.valueOf(geneInfo[1]), Integer.valueOf(geneInfo[2]), passFilter, this.currFile.getPath());
                        data.addAll(retrievedVariants);
                        if (!helperMap.containsKey(retrievedChr)) {
                            helperMap.put(retrievedChr, new HashMap<String, List<String[]>>());
                        }
//                        helperMap.get(retrievedChr).put(gene.get(i) + ":" + geneInfo[3], new HashSet<String[]>(retrievedVariants));
                        helperMap.get(retrievedChr).put(gene.get(i) + ":" + geneInfo[3], retrievedVariants);

                    }
                }
                title = "HeatMap of Read Depth by Sample, PassFilter: " + passFilter;
                break;
        }
        if(data.size() == 0) {
//            System.out.println("REQUEST LOGIC DONE");
            return null;
        }
        MapView map = new MapView(title, passFilter);
        if (this.phenotypeMap.containsKey(this.currFile)) {
            PhenotypeReader phenotypeInfo = this.phenotypeMap.get(this.currFile);
            map.generateGroupInfo(phenotypeInfo.getGroupToPatientsMap());
        }
        map.populateHeatMap(helperMap, new ArrayList<String>(Arrays.asList(this.infoMap.get(this.currFile).getAllPatients().split("\t"))), data);
//        System.out.println("REQUEST LOGIC DONE");
        return map;
    }

    public TreeView generateTree(MapState type, String passFilter, String chr, String gene,
                                 String start, String end) throws IOException {

        List<String[]> data = new ArrayList<String[]>();
        String retrievedChr = null;
        String[] geneInfo = this.geneParser.getGeneLocation(gene);
        if (geneInfo != null) {
//            System.out.println("Gene found on chr: " + geneInfo[0] + ", StartPos: " + geneInfo[1] + ", endPos: " + geneInfo[2] + ", emsemble ID: " + geneInfo[3]);
            retrievedChr = geneInfo[0].substring(3);
            List<String[]> retrievedLines = this.vcfParser.getLinesByPos(retrievedChr, this.getChrStart(retrievedChr), this.getChrEnd(retrievedChr),
                    Integer.valueOf(geneInfo[1]), Integer.valueOf(geneInfo[2]), passFilter, this.currFile.getPath());
            data.addAll(retrievedLines);
        }
        String title = "Tree view of gene: " + gene + ", Pass Filter: " + passFilter;
        if(data.size() == 0) {
//            System.out.println("REQUEST LOGIC DONE");
            return null;
        }
        TreeView tree = new TreeView(gene, title, retrievedChr);
        tree.populateTree(data, retrievedChr);
//        System.out.println("REQUEST LOGIC DONE");
        return tree;
    }

    private int getChrStart(String chr) {
        return this.indexMap.get(this.currFile).getStartLine(chr);
    }

    private int getChrEnd(String chr) {
        return this.indexMap.get(this.currFile).getEndLine(chr);
    }
}


//TREEVIEW SAVE
//
//    public TreeView generateTree(MapState type, String passFilter, String chr, String gene,
//                                 String start, String end) throws IOException {
//
//        List<String[]> data = new ArrayList<String[]>();
//        String title = null;
//        switch (type) {
//            case RANGE:
//                List<String[]> retrievedRange = this.vcfParser.getLinesByPos(this.getChrStart(chr),this.getChrEnd(chr),
//                        Integer.valueOf(start), Integer.valueOf(end), passFilter, this.currFile.getPath());
//                data.addAll(retrievedRange);
//                title = "Tree view of genes for range: " + chr + ":" + start + "-" + end + ", Pass Filter: " + passFilter;
//                break;
//            case GENE:
//                String[] geneInfo = this.geneParser.getGeneLocation(gene);
//                if (geneInfo != null) {
////                    System.out.println("Gene found on chr: " + geneInfo[0] + ", StartPos: " + geneInfo[1] + ", endPos: " + geneInfo[2] + ", emsemble ID: " + geneInfo[3]);
//                    String retrievedChr = geneInfo[0].substring(3);
//                    List<String[]> retrievedLines = this.vcfParser.getLinesByPos(this.getChrStart(retrievedChr), this.getChrEnd(retrievedChr),
//                            Integer.valueOf(geneInfo[1]), Integer.valueOf(geneInfo[2]), passFilter, this.currFile.getPath());
//                    data.addAll(retrievedLines);
//                }
//                title = "Tree view of gene: " + gene + ", Pass Filter: " + passFilter;
//                break;
//        }
//        if(data.size() == 0) {
////            System.out.println("REQUEST LOGIC DONE");
//            return null;
//        }
//        TreeView tree = new TreeView(gene, title);
//        tree.populateTree(data);
////        System.out.println("REQUEST LOGIC DONE");
//        return tree;
//    }
