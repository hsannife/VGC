package com.example.variantgraphcraftbackend.service;
import com.example.variantgraphcraftbackend.model.UploadedFile;
import com.example.variantgraphcraftbackend.model.filemanager.*;
import org.jboss.jandex.Index;

import javax.persistence.criteria.CriteriaBuilder;
import java.io.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class VCFParser {

    private HashMap<UploadedFile, InfoReader> infoMap;
    private HashMap<UploadedFile, IndexReader> indexMap;

    public VCFParser(HashMap<UploadedFile, InfoReader> infoMap,
                     HashMap<UploadedFile, IndexReader> indexMap) throws FileNotFoundException {
        this.infoMap = infoMap;
        this.indexMap = indexMap;
    }

    public void processSelectedFile(UploadedFile file) throws IOException {
//        System.out.println("Creating support files...");

        File vcf = new File(file.getPath());
        String name = vcf.getName();
        name = name.substring(0, name.length() - 4);

        File info = new File("VGC_" + name + "/info_" + name + ".txt");
        File index = new File("VGC_" + name + "/index_" + name + ".txt");

        if(!info.exists() && !index.exists()) {
            File directory = new File("VGC_" + name);
            directory.mkdir();
            InfoWriter infoWriter = new InfoWriter(info);
            IndexWriter indexWriter = new IndexWriter(index);

//            System.out.println("The name is " + name + ". The new paths are: ");
//            System.out.println(info.getAbsolutePath());
//            System.out.println(index.getAbsolutePath());

            this.read(file, infoWriter, indexWriter);
            infoWriter.addChrom(indexWriter.getNumChrom(), indexWriter.getChromList());
            infoWriter.writeInfo();
            indexWriter.writeIndex();
        }

        InfoReader infoReader = new InfoReader(info.getAbsolutePath());
        IndexReader indexReader= new IndexReader(index.getAbsolutePath());
        infoReader.readInfo();
        indexReader.readIndex();
        this.infoMap.put(file, infoReader);
        this.indexMap.put(file, indexReader);
//        System.out.println("Files retrieved and read.");
    }


    /**
     * Initial read of an added vcf file. Called once a new
     * UploadedFile is saved to the FileRepository.
     */
    private void read(UploadedFile file, InfoWriter infoWriter, IndexWriter indexWriter) throws IOException {
        String currLine = "";
        String prevLine = "";
        int lineNumber = 0;
        String path = file.getPath();
        File vcf = new File(path);
        BufferedReader input = new BufferedReader(new FileReader(vcf));
        PathogenicParser pathogenicParser = new PathogenicParser();
        pathogenicParser.loadMapping();

        //Updates version in InfoFile through UploadedFile.
        currLine = input.readLine();
        infoWriter.addVersion(currLine);

        //Reads rest of file + updates info/index accordingly.
        while(currLine != null) {
            lineNumber++;
            if (currLine.startsWith("##")) {
                //Analyze metadata
            } else if (currLine.startsWith("#CHROM")) {
                infoWriter.addHeader(currLine);
            } else {
                indexWriter.buildChromData(prevLine, currLine, lineNumber, pathogenicParser);
            }
            prevLine = currLine;
            currLine = input.readLine();
        }
        input.close();
        indexWriter.addLastChrom(prevLine, lineNumber);
//        System.out.println("Finished reading. We are at line " + lineNumber + ".");
    }

    public List<String[]> getLinesByTotal(int total, int startLine, int endLine, String vcf) throws IOException{
        List<String[]> varList = new ArrayList<String[]>();
        int counter = 0;
        BufferedReader input = new BufferedReader(new FileReader(vcf));
        String currLine = input.readLine();
        counter++;
        while(currLine != null) {
            if(counter >= startLine) {
                if(counter > endLine || counter >= (startLine + total)) {
//                    System.out.println("Done in vcfParser.");
                    input.close();
                    return varList;
                }
                String[] split = currLine.split("\t");
                varList.add(split);

            }
            currLine = input.readLine();
            counter++;
        }
        input.close();
//        System.out.println("Done in vcfParser.");
        return varList;
    }

    public List<String[]> getLines(int startLine, int endLine, String passFilter, String vcf) throws IOException {
//        System.out.println("In getLines...");
        List<String[]> varList = new ArrayList<String[]>();

        BufferedReader input = new BufferedReader(new FileReader(vcf));
        String currLine = input.readLine();
        int counter = 1;
        if (passFilter.equals("ALL")) {
//            System.out.println("Passfilter is: " + passFilter);
            while(currLine != null) {
                if(counter >= startLine) {
                    if(counter > endLine) {
//                        System.out.println("Done in vcfParser.");
                        input.close();
                        return varList;
                    }
                    String[] split = currLine.split("\t");
                    varList.add(split);
                }
                currLine = input.readLine();
                counter++;
            }
        } else {
//            System.out.println("Passfilter is: " + passFilter);
            while(currLine != null) {
                if(counter >= startLine) {
                    if(counter > endLine) {
//                        System.out.println("Done in vcfParser.");
                        input.close();
                        return varList;
                    }
                    String[] split = currLine.split("\t");
                    if (split[6].equals(passFilter)) {
                        varList.add(split);
                    }
                }
                currLine = input.readLine();
                counter++;
            }
        }
        input.close();
//        System.out.println("Done in vcfParser.");
        return varList;
    }

    public List<String[]> getLinesByPos(String chr, int startLine, int endLine, int startPos, int endPos, String passFilter, String vcf) throws IOException {
//        System.out.println("In getLinesByPos...");
        List<String[]> varList = new ArrayList<String[]>();
        int counter = 0;
        BufferedReader input = new BufferedReader(new FileReader(vcf));
        String currLine = input.readLine();
        counter++;
        if (passFilter.equals("ALL")) {
//            System.out.println("Passfilter is: " + passFilter);
            while(currLine != null) {
                if(counter >= startLine) {
                    if(counter > endLine) {
//                        System.out.println("Done in vcfParser.");
                        input.close();
                        return varList;
                    }
                    String[] split = currLine.split("\t");
                    int currPos = Integer.valueOf(split[1]);
                    if (currPos >= startPos && currPos <= endPos) {
                        varList.add(split);
                    }
                }
                currLine = input.readLine();
                counter++;
            }
        } else if (passFilter.equals("PASS")){
//            System.out.println("Passfilter is: " + passFilter);
            while(currLine != null) {
                if(counter >= startLine) {
                    if(counter > endLine) {
//                        System.out.println("Done in vcfParser.");
                        input.close();
                        return varList;
                    }
                    String[] split = currLine.split("\t");
                    int currPos = Integer.valueOf(split[1]);
                    if (currPos >= startPos && currPos <= endPos && split[6].equals(passFilter)) {
                        varList.add(split);
//                        System.out.println("LINE ADDED. LINE NUMBER IS: " + counter);
////                        System.out.println(currLine);
                    }
                }
                currLine = input.readLine();
                counter++;
            }
        } else {
            PathogenicParser pathogenicParser = new PathogenicParser();
            pathogenicParser.loadMapping();
//            System.out.println("Passfilter is: " + passFilter);
            while(currLine != null) {
                if(counter >= startLine) {
                    if(counter > endLine) {
//                        System.out.println("Done in vcfParser.");
                        input.close();
                        return varList;
                    }
                    String[] split = currLine.split("\t");
                    int currPos = Integer.valueOf(split[1]);
                    if (currPos >= startPos && currPos <= endPos && pathogenicParser.isPathogenic(chr, split[1])) {
                        varList.add(split);
//                        System.out.println("LINE ADDED. LINE NUMBER IS: " + counter);
////                        System.out.println(currLine);
                    }
                }
                currLine = input.readLine();
                counter++;
            }
        }
        input.close();
//        System.out.println("Done in vcfParser.");
        return varList;
    }

    private int getPos(String varLine) {
        String pos = varLine.split("\t")[1];
        return Integer.valueOf(pos);
    }


    public HashMap<Integer, ArrayList<String[]>> getChromHistrogramData(String chr, int range, HashMap<Integer, Integer> histogramData,
                                                                       HashMap<Integer, ArrayList<String[]>> posMap,
                                                                       int startLine, int endLine, int start, int end,
                                                                       String passFilter, String vcf) throws IOException {
        int counter = 0;
        BufferedReader input = new BufferedReader(new FileReader(vcf));
        String currLine = input.readLine();
        counter++;
        if (passFilter.equals("ALL")) {
            while(currLine != null) {
                if(counter >= startLine) {
                    if(counter > endLine) {
//                        System.out.println("ChromHistogramData retrieved.");
                        input.close();
                        return posMap;
                    }
                    String[] variantArray = currLine.split("\t");
                    int pos = Integer.valueOf(variantArray[1]);
                    if (pos >= start && pos <= end) {
                        int rounded = pos / range;
                        rounded = rounded * range;
                        histogramData.put(rounded, histogramData.get(rounded) + 1);
//                        posMap.get(rounded).add(pos);
                        posMap.get(rounded).add(variantArray);
                    }
                }
                currLine = input.readLine();
                counter++;
            }
        } else if (passFilter.equals("PASS")){
            while(currLine != null) {
                if(counter >= startLine) {
                    if(counter > endLine) {
//                        System.out.println("ChromHistogramData retrieved.");
                        input.close();
                        return posMap;
                    }
                    String[] variantArray = currLine.split("\t");
                    int pos = Integer.valueOf(variantArray[1]);
                    String filter = variantArray[6];
                    if (pos >= start && pos <= end && filter.equals(passFilter)) {
                        int rounded = pos / range;
                        rounded = rounded * range;
                        histogramData.put(rounded, histogramData.get(rounded) + 1);
//                        posMap.get(rounded).add(pos);
                        posMap.get(rounded).add(variantArray);
                    }
                }
                currLine = input.readLine();
                counter++;
            }
        } else {
            PathogenicParser pathogenicParser = new PathogenicParser();
            pathogenicParser.loadMapping();
            while(currLine != null) {
                if(counter >= startLine) {
                    if(counter > endLine) {
//                        System.out.println("ChromHistogramData retrieved.");
                        input.close();
                        return posMap;
                    }
                    String[] variantArray = currLine.split("\t");
                    int pos = Integer.valueOf(variantArray[1]);
//                    String filter = variantArray[6];
                    if (pos >= start && pos <= end && pathogenicParser.isPathogenic(chr, variantArray[1])) {
                        int rounded = pos / range;
                        rounded = rounded * range;
                        histogramData.put(rounded, histogramData.get(rounded) + 1);
//                        posMap.get(rounded).add(pos);
                        posMap.get(rounded).add(variantArray);
                    }
                }
                currLine = input.readLine();
                counter++;
            }
        }
        input.close();
        return posMap;
    }

    public HashMap<Integer, Integer> getChromHistrogramData(String chr, int range, HashMap<Integer, Integer> histogramData,
                                                            int startLine, int endLine, String passFilter, String vcf) throws IOException {
        PathogenicParser pathogenicParser = new PathogenicParser();
        pathogenicParser.loadMapping();
        int counter = 0;
        BufferedReader input = new BufferedReader(new FileReader(vcf));
        String currLine = input.readLine();
        counter++;
        while(currLine != null) {
            if(counter >= startLine) {
                if(counter > endLine) {
//                    System.out.println("ChromHistogramData retrieved.");
                    input.close();
                    return histogramData;
                }
                String[] variantArray = currLine.split("\t");
                int pos = Integer.valueOf(variantArray[1]);
                if (passFilter.equals("ALL")) {
                    int rounded = pos / range;
                    rounded = rounded * range;
                    histogramData.put(rounded, histogramData.get(rounded) + 1);
                } else if (passFilter.equals("PASS")){
                    String filter = variantArray[6];
                    if (filter.equals(passFilter)) {
                        int rounded = pos / range;
                        rounded = rounded * range;
                        histogramData.put(rounded, histogramData.get(rounded) + 1);
                    }
                } else {
                    if (pathogenicParser.isPathogenic(chr, variantArray[1])) {
                        int rounded = pos / range;
                        rounded = rounded * range;
                        histogramData.put(rounded, histogramData.get(rounded) + 1);
                    }
                }
            }
            currLine = input.readLine();
            counter++;
        }
        input.close();
        return histogramData;
    }

    public void saveToMap(HashMap<String, String[]> saveMap, String[] var, String chr) {
        saveMap.put(chr, var);
    }



    public String getVersion(UploadedFile file) {
        return this.infoMap.get(file).getVersion();
    }

    public String getNumPatients(UploadedFile file) {
        return this.infoMap.get(file).getPatients();
    }

    public int getChromosomes(UploadedFile file) {
        return this.infoMap.get(file).getNumChrom();
    }
}


//    public HashMap<String, Integer> getLineByPosDP(int startLine, int endLine, int patientNum, String vcf) throws IOException{
//        int counter = 0;
//        HashMap<String, Integer> dpData = new HashMap<String, Integer>();
//        BufferedReader input = new BufferedReader(new FileReader(vcf));
//        String currLine = input.readLine();
//        counter++;
//        while(currLine != null) {
//            if(counter >= startLine) {
//                if(counter > endLine) {
////                    System.out.println("done");
//                    input.close();
//                    return dpData;
//                }
//                String[] variant = currLine.split("\t");
//                String[] patientEntry = variant[8 + patientNum].split(":");
//                dpData.put(variant[1], Integer.valueOf(patientEntry[2]));
//            }
//            currLine = input.readLine();
//            counter++;
//        }
//        input.close();
//        return dpData;
//    }