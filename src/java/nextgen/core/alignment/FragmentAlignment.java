package nextgen.core.alignment;

import java.util.HashMap;

import nextgen.core.annotation.BasicAnnotation;

import org.apache.log4j.Logger;

/**
 * @author prussell
 * A full fragment alignment constructed from two paired reads
 */
public class FragmentAlignment extends AbstractPairedEndAlignment {

	private static Logger logger = Logger.getLogger(PairedReadAlignment.class.getName());

	
    /**
     * Constructs a paired end alignment object from two alignments.
     * @param read1
     * @param read2
     */
    public FragmentAlignment(SingleEndAlignment read1, SingleEndAlignment read2) {
    	super(asAnnotation(read1, read2, true));   //TODO How to deal with pairs that have different chromosomes?

    	this.firstMate = read1;
        this.secondMate = read2;
        attributeMap = new HashMap<String,String>();
    }
    
    /**
     * Constructs a paired end alignment object from two alignments and provides the read that is in the direction of transcription
     * @param read1
     * @param read2
     * @param strand
     */
    public FragmentAlignment(SingleEndAlignment read1, SingleEndAlignment read2,TranscriptionRead strand) {
    	super(asAnnotation(read1, read2, true));   //TODO How to deal with pairs that have different chromosomes?

    	this.firstMate = read1;
        this.secondMate = read2;
        this.txnRead = strand;
        attributeMap = new HashMap<String,String>();
    }
    
    @Override
	public boolean equals(Object o) {
		if(!o.getClass().equals(FragmentAlignment.class)) {
			return false;
		}
		FragmentAlignment f = (FragmentAlignment)o;
		return f.getFirstMate().equals(getFirstMate()) && f.getSecondMate().equals(getSecondMate());
	}

	@Override
	public int hashCode() {
		return (getFirstMate().toString() + getSecondMate().toString()).hashCode();
	}

}