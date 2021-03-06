package xp.core.command;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import net.sf.picard.cmdline.CommandLineProgram;
import net.sf.picard.cmdline.Option;
import net.sf.picard.cmdline.Usage;
import nextgen.core.annotation.Annotation;
import nextgen.core.coordinatesystem.GenomicSpace;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.log4j.Logger;

import xp.core.Basic.Peak;
import xp.core.Basic.PeakFactory;
import xp.core.Basic.SkellamScoreMachine;
import xp.core.Converter.BamIteratorFactory;
//import xp.test.DBI.ShortBEDTabixDBI;


/**
 * 2013-4-5
 * 
 * @author zhuxp
 * 
 * 
 * This Program Call Peaks and calculate the difference between two ChIP Seq data in two conditions;
 * INPUT file is 
 * CASEA : Case A condition BAM file
 * CASEB : Case B condition BAM file
 * CONTROLA :  Control A BAM file
 * CONTROLB :  Control B BAM file
 * 
 * Step 1. Call Peaks
 *   1. base pair solution
 *   2. pvalue is based on skellam distribution
 *      min ( pvalue of CASEA vs CONTROLA,
 *            pvalue of CASEB vs CONTROLB,
 *            pvalue of CASEA+CASEB vs CONTROLA+CONTROLB )
 *   3. pvalue of CASE vs CONTROL 
 *      max ( pvalue based environment lambda,
 *            pvalue based global lambda )
 *   4. merge near by bases, if Pvalue < Threshold(default: 10^-8 setup in ScoreMachine) and gap < MAXGAP (default: 200 bp setup in PeakFactory)          
 *   5. calculate average coverage in merge region for each peak;
 *   
 * Step 2 Report Peaks Diff
 *   1. FoldChangeA  = CaseA vs ControlA (Normalized) 
 *   2. FoldChangeB  = CaseB vs ControlB (Normalized)
 *   3. Indicator = log2( FoldChangeA / FoldChangeB )
 *   
 *   FoldChange Definition:
 *       FoldChange is defined as below: 
		 FoldChange = max ( 1.0, (CASE/caseGlobalLambda))/max(1.0,(CONTROL/controlGlobalLambda))
		 FoldChange always >= 1.0
	 
	
 *   
 * TODO:
 *     Characterize Indicator value distribution ( how many Gaussian distributions in it)
 *     report each peak belong to which Gaussian and the pvalue
 *     OR report the significant change peaks.
 *       
 */




public class CallPeakDiff extends CommandLineProgram{
	static Logger logger = Logger.getLogger(CallPeakDiff.class.getName());	
	
	private static final String PROGRAM_VERSION = "0.02";
	
    
	@Usage

    @Option(doc="This is caseA bam", shortName="A") public File CASEA;
    @Option(doc="This is caseB bam", shortName="B") public File CASEB;
    @Option(doc="This is control A bam", shortName="X") public File CONTROLA;
    @Option(doc="This is control B bam", shortName="Y") public File CONTROLB;
    @Option(doc="Chromosome Size",shortName="G") public String CHROMSIZES;
    @Option(doc="output file",shortName="o") public String FOUT="tmp.CallPeakDiff.out";
    @Option(doc="paired end",shortName="p") public boolean ISPAIRED;
    @Option(doc="if CaseA Paired", shortName="PA") public boolean CASEAPAIRED;
    @Option(doc="if CaseB Paired", shortName="PB") public boolean CASEBPAIRED;
    @Option(doc="if ControlA Paired", shortName="PX") public boolean CONTROLAPAIRED;
    @Option(doc="if ControlB Paired", shortName="PY") public boolean CONTROLBPAIRED;
    //@Option(doc="data format", shortName="f") public String FORMAT="bam";
    @Option(doc="window size (count reads number nearby)", shortName="w") public int WINDOWSIZE=0; 
    @Option(doc="print peak detail. default: false",shortName="d") public boolean PEAKDETAIL=false;
    @Option(doc="peak prefix") public String PREFIX="Peak";
    @Option(doc="score threshold") public Double SCORETHRESHOLD=8.0;
    @Option(doc="max gap") public int MAXGAP=200;
    @Option(doc="max allowable gap between two paired end reads") public int MAXALLOWABLEINSERT=5000;
    public static void main(String[] argv){
    	System.exit(new CallPeakDiff().instanceMain(argv));
        
    }

	@Override
	protected int doWork() {
		// TODO Auto-generated method stub
	    //logger.setLevel(Level.DEBUG);
		
		 boolean ignoreBlocksDetails[]= new boolean[4];
		 for (int i = 0; i < ignoreBlocksDetails.length; i++) {
			ignoreBlocksDetails[i]=false;
		}
    	 logger.info("initialize genomic space");
    	 GenomicSpace GENOMESPACE = new GenomicSpace(CHROMSIZES);
    	 logger.info("initialize genomic space done");
    	
    	 logger.info(GENOMESPACE.getReferenceNames());
    	 try {
    	 String isCaseAPaired="s";
    	 String isCaseBPaired="s";
    	 String isControlAPaired="s";
    	 String isControlBPaired="s";
    	 if (CASEAPAIRED) isCaseAPaired="p";
    	 if (CASEBPAIRED) isCaseBPaired="p";
    	 if (CONTROLAPAIRED) isControlAPaired="p";
    	 if (CONTROLBPAIRED) isControlBPaired="p";
    	 
    	 if(ISPAIRED) 
    	 {
    	  isCaseAPaired="p";
    	  isCaseBPaired="p";
    	  isControlAPaired="p";
    	  isControlBPaired="p";
    	 }
    	 Iterator<? extends Annotation> caseAIter ; 
    	 Iterator<? extends Annotation> caseBIter ; 
    	 Iterator<? extends Annotation> controlAIter ; 
    	 Iterator<? extends Annotation> controlBIter ; 
    	 
    	 /*  
    	 if(FORMAT.equalsIgnoreCase("shortbed"))
    	 {
    		 ShortBEDTabixDBI caseADBI= new ShortBEDTabixDBI(CASEA.getAbsolutePath(),GENOMESPACE);
        	 ShortBEDTabixDBI caseBDBI= new ShortBEDTabixDBI(CASEB.getAbsolutePath(),GENOMESPACE);
        	 ShortBEDTabixDBI controlADBI= new ShortBEDTabixDBI(CONTROLA.getAbsolutePath(),GENOMESPACE);
        	 ShortBEDTabixDBI controlBDBI= new ShortBEDTabixDBI(CONTROLB.getAbsolutePath(),GENOMESPACE);
        	 caseAIter=caseADBI.iterate();
        	 caseBIter=caseBDBI.iterate();
        	 controlAIter=controlADBI.iterate();
        	 controlBIter=controlBDBI.iterate();
    	 }
    	 
    	 else
    	
    	 {
    	 */
    	 caseAIter = BamIteratorFactory.makeIterator(CASEA,isCaseAPaired, MAXALLOWABLEINSERT); 
    	 caseBIter = BamIteratorFactory.makeIterator(CASEB,isCaseBPaired, MAXALLOWABLEINSERT); 
    	 controlAIter = BamIteratorFactory.makeIterator(CONTROLA,isControlAPaired, MAXALLOWABLEINSERT); 
    	 controlBIter = BamIteratorFactory.makeIterator(CONTROLB,isControlBPaired, MAXALLOWABLEINSERT); 
    	 //}
    	 
    	 @SuppressWarnings("unchecked")
		Iterator<? extends Annotation>[] iters=new Iterator[4];
    	 iters[0]=caseAIter;
    	 iters[1]=controlAIter;
    	 iters[2]=caseBIter;
    	 iters[3]=controlBIter;
    	
    	 
    	 /*
    	  * if is paired end 
    	  * ignore the blocks detail
    	  */
    	 if (isCaseAPaired.equalsIgnoreCase("p")) ignoreBlocksDetails[0]=true;
    	 if (isCaseBPaired.equalsIgnoreCase("p")) ignoreBlocksDetails[2]=true;
    	 if (isControlAPaired.equalsIgnoreCase("p")) ignoreBlocksDetails[1]=true;
    	 if (isControlBPaired.equalsIgnoreCase("p")) ignoreBlocksDetails[3]=true;
    	 
    	 FileWriter out=new FileWriter(FOUT);
    	 FileWriter peakOut = null;
    	 if(PEAKDETAIL)
    		 peakOut = new FileWriter(FOUT+".detail.peak");
    	 
    	 SkellamScoreMachine scoreMachine = new SkellamScoreMachine();
    	 scoreMachine.setThreshold(SCORETHRESHOLD);
    	 PeakFactory<SkellamScoreMachine>  peakFactory= new PeakFactory<SkellamScoreMachine>(iters,GENOMESPACE,scoreMachine,WINDOWSIZE,ignoreBlocksDetails);
    	 peakFactory.setPrefix(PREFIX);
    	 peakFactory.setMAXGAP(MAXGAP);
    	 int i=0;
    	 out.write("# Table Generated by Program CallPeakDiff "+PROGRAM_VERSION+" \n");
    	 DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    	 Date date = new Date();
    	 out.write("# "+dateFormat.format(date)+"\n");
    	 out.write("# CASEA : "+CASEA + "\n");
    	 out.write("# CONTROLA : "+CONTROLA + "\n");
    	 out.write("# CASEB : "+CASEB + "\n");
    	 out.write("# CONTROLB : "+CONTROLB +"\n");
    	 out.write("# GLOBAL AVERAGE COVERAGE :" + Arrays.toString(peakFactory.getGlobalLambdas())+ "\n");
    	 out.write("# GENOME SIZE: "+ GENOMESPACE.getLength() + "\n");
    	 out.write("# COMMAND LINE:   "+this.getCommandLine()+"\n");
    	 out.write(getIllustration());
    	 out.write(String.format("# chr\tbeg\tend\tid\tscore\t(A+B)\tA/(A+B)\tCaseA\tCtrlA\tCaseB\tCtrlB\tFoldChangeA\tFoldChangeB\tlog2(FoldChangeA/FoldChangeB)\n"));
    	 
    	 
    	 SummaryStatistics stats = new SummaryStatistics();
    	 while(peakFactory.hasNext())
    	 {
    		 i++;
    		 Peak peak=peakFactory.next();
    		 
    		 out.write(peak.toSimpleBED());
    		 double[] averageScores=peak.getAverageScores();
    		 double[] normalizeScores=peak.getNormalizedScores(peakFactory.getGlobalLambdas());
    		 if (PEAKDETAIL)
    		 {
    		 peakOut.write(peak.toPeakDetail());
    		 peakOut.write("\n");
    		 }
    		 
    		 
    		 
    		 out.write(String.format("\t%.2f",averageScores[0]+averageScores[2]) );
    		 out.write(String.format("\t%.4f",averageScores[0]/(averageScores[0]+averageScores[2])) );
    		 
    		 
    		 for (int j = 0; j < averageScores.length; j++) {
				out.write(String.format("\t%.4f",averageScores[j]));
			}
    		 
    		 
    		 
    		 /****  FOLD CHANGE  PART *&****/
    		 double foldChangeA;
    		 double foldChangeB;
    		 if (normalizeScores[1] > 1.0) 
    			 foldChangeA=normalizeScores[0]/normalizeScores[1];
    		 else
    			 foldChangeA=normalizeScores[0];	 
    		if (normalizeScores[3] > 1.0)
    			foldChangeB=normalizeScores[2]/normalizeScores[3];
    		else
    			foldChangeB=normalizeScores[2];
    		foldChangeA=Math.max(foldChangeA, 1.0);
    		foldChangeB=Math.max(foldChangeB, 1.0);
            out.write(String.format("\t%.4f",foldChangeA));    		 
            out.write(String.format("\t%.4f",foldChangeB));
            double logFoldChangeDiff = Math.log(foldChangeA/foldChangeB)/Math.log(2.0);
            stats.addValue(logFoldChangeDiff);
            out.write(String.format("\t%.4f", logFoldChangeDiff));    		
            
            
            /*******************************/
            out.write("\n");
            
    		 if(i%1000==0)
    			 logger.info(i+" peaks");
    	 }
    	 logger.info(stats.getSummary());
    	 out.close();
    	 if(PEAKDETAIL) peakOut.close();
    	 
    	 }
    	 catch (IOException e)
     	{
     	//To do	
    		logger.error(e);
     	}
    	 return 0;
	}
	
	
	private static String getIllustration()
	{
		String s="";
		s+="# FoldChange is defined as below: \n";
		s+="# FoldChange = max ( 1.0, (CASE/caseGlobalLambda))/max(1.0,(CONTROL/controlGlobalLambda)) \n";
		s+="# FoldChange always >= 1.0\n";
		s+="# \n";
		return s;
	}
	
    
    
}
