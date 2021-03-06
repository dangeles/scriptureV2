package xp.core.command;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import net.sf.picard.cmdline.CommandLineProgram;
import net.sf.picard.cmdline.Option;
import net.sf.picard.cmdline.Usage;
import nextgen.core.annotation.Annotation;
import nextgen.core.coordinatesystem.GenomicSpace;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import xp.core.Basic.Peak;
import xp.core.Basic.PeakFactory;
import xp.core.Basic.PoissonScoreMachine;
import xp.core.Converter.BamIteratorFactory;
import xp.core.DBI.ShortBEDTabixDBI;

/**
 * 2013-4-3
 * 
 * @author zhuxp
 * 
 * CallPeak for one case and one control
 * 
 * Output the stats of peak detection
 * to evaluate the enrichment of ChIPSeq in peaks.
 *
 * and Peaks region
 * 
 */




public class CallPeakPoisson extends CommandLineProgram{
	static Logger logger = Logger.getLogger(CallPeakPoisson.class.getName());	
	
	private static final String PROGRAM_VERSION = "0.01";
    //report the region which at least have on fragment score larger than the MINPEAK SCORE
	@Usage
	public static final String USAGE = "Usage: CallPeakPoisson [options]";
    @Option(doc="This is caseA bam", shortName="A") public File CASEA;
    @Option(doc="Chromosome Size",shortName="G") public String CHROMSIZES;
    @Option(doc="output file",shortName="o") public String FOUT="tmp.CallPeakPossion.out";
    // TO DO Fix the paired end 
    //@Option(doc="paired end",shortName="p") public boolean ISPAIRED=false;
    @Option(doc="data format", shortName="f") public String FORMAT="bam"; 
    @Option(doc="window size (count reads number nearby)", shortName="w") public int WINDOWSIZE=0; 
    
    
    public static void main(String[] argv){
    	System.exit(new CallPeakPoisson().instanceMain(argv));
        
    }

	@Override
	protected int doWork() {
		// TODO Auto-generated method stub
		 logger.setLevel(Level.DEBUG);
		 
		 GenomicSpace GENOMESPACE = new GenomicSpace(CHROMSIZES);
    	 try {
    	 String isPaired="s";
    	// if(ISPAIRED) isPaired="p"; //TO DO FIX the Paired END ITERATOR
    	 Iterator<? extends Annotation> caseAIter ; 
    	 //Iterator<? extends Annotation> controlAIter ; 
    	 if(FORMAT.equalsIgnoreCase("shortbed"))
    	 {
    		 ShortBEDTabixDBI caseADBI= new ShortBEDTabixDBI(CASEA.getAbsolutePath(),GENOMESPACE);
        	 //ShortBEDTabixDBI controlADBI= new ShortBEDTabixDBI(CONTROLA.getAbsolutePath(),GENOMESPACE);
        	 caseAIter=caseADBI.iterate();
        	 //controlAIter=controlADBI.iterate();
    	 }
    	 else
    	
    	 {
    	 //caseAIter = BamIteratorFactory.makeIterator(CASEA,isPaired); 
    	 caseAIter = BamIteratorFactory.makeIterator(CASEA); 
    	 //controlAIter = BamIteratorFactory.makeIterator(CONTROLA,isPaired); 
    	 //controlAIter = BamIteratorFactory.makeIterator(CONTROLA); 
    	 }
    	 
    	 Iterator<? extends Annotation>[] iters=new Iterator[1];
    	 iters[0]=caseAIter;
    	 //iters[1]=controlAIter;
    	 
    	 FileWriter out=new FileWriter(FOUT);
    	 PoissonScoreMachine scoreMachine = new PoissonScoreMachine();
    	 PeakFactory<PoissonScoreMachine>  peakFactory= new PeakFactory<PoissonScoreMachine>(iters,GENOMESPACE,scoreMachine,WINDOWSIZE);
    	 int i=0;
    	 while(peakFactory.hasNext())
    	 {
    		 i++;
    		 Peak peak=peakFactory.next();
    		 out.write(peak.toSimpleBED());
    		 out.write("\t");
    		 out.write(Arrays.toString(peak.getAverageScores()));
    		 double[] nScores = peak.getNormalizedScores(peakFactory.getGlobalLambdas());
    		 for (int j = 0; j < nScores.length; j++) {
				out.write("\t");
				out.write(Double.toString(nScores[j]));
			}
    		 out.write("\n");
    		 if(i%1000==0)
    			 logger.info(i+" peaks");
    	 }
    	 out.close();
    	 
    	 }
    	 catch (IOException e)
     	{
     	//To do	
    		logger.error(e);
     	}
    	 return 0;
	}
	
	
	
	
    
    
}
