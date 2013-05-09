/**
 * 
 */
package nextgen.core.writers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.collections15.Predicate;
import org.apache.log4j.Logger;

import broad.core.datastructures.IntervalTree;
import broad.core.datastructures.IntervalTree.Node;
import broad.core.parser.CommandLineParser;
import broad.pda.annotation.BEDFileParser;
import net.sf.samtools.util.CloseableIterator;
import nextgen.core.alignment.Alignment;
import nextgen.core.annotation.Annotation;
import nextgen.core.annotation.Gene;
import nextgen.core.coordinatesystem.GenomicSpace;
import nextgen.core.coordinatesystem.TranscriptomeSpace;
import nextgen.core.model.AlignmentModel;
import nextgen.core.model.score.CountScore;
import nextgen.core.model.score.WindowScoreIterator;
import nextgen.core.normalize.NormalizedCount;
import nextgen.core.normalize.RawCounts;
import nextgen.core.normalize.TranscriptAverageNormalization;
import nextgen.core.readFilters.GenomicSpanFilter;

/**
 * @author prussell
 *
 */
public class WigWriter {
	
	private TranscriptomeSpace transcriptomeSpace;
	private GenomicSpace genomeSpace;
	private Map<String, Collection<Gene>> genesByChr;
	private AlignmentModel data;
	private AlignmentModel normData;
	private NormalizedCount normalization;
	protected Collection<String> chrNames;
	private boolean readBeginningPositionOnly;
	private boolean isTranscriptomeSpace;
	private boolean normalize;
	private boolean bothFiles = false;
	protected static boolean DEFAULT_USE_FRAGMENTS = true;
	protected static int DEFAULT_MAX_FRAGMENT_LENGTH = 2000;
	protected static int DEFAULT_MAX_GENOMIC_SPAN = 300000;
	static Logger logger = Logger.getLogger(WigWriter.class.getName());

	/**
	 * Construct with a transcriptome space
	 * @param bamFile Bam alignments
	 * @param genesByChrName Genes by reference sequence name
	 * @param firstPositionOnly Only count the first position of each read
	 * @param useFragments Whether to convert reads to fragments
	 * @param nor Normalize by global amounts
	 */
	public WigWriter(String bamFile, Map<String, Collection<Gene>> genesByChrName, boolean firstPositionOnly, boolean useFragments, boolean nor) {
		this(bamFile, genesByChrName, firstPositionOnly, useFragments, nor, null);
	}
	
	/**
	 * Construct with a transcriptome space and only write one chromosome
	 * @param bamFile Bam alignments
	 * @param genesByChrName Genes by reference sequence name
	 * @param firstPositionOnly Only count the first position of each read
	 * @param useFragments Whether to convert reads to fragments
	 * @param nor Normalize by global amounts
	 * @param chrToWrite Only write this chromosome
	 */
	public WigWriter(String bamFile, Map<String, Collection<Gene>> genesByChrName, boolean firstPositionOnly, boolean useFragments, boolean nor, String chrToWrite) {
		genesByChr = genesByChrName;
		chrNames = new TreeSet<String>();
		if(chrToWrite == null) {
			chrNames.addAll(genesByChr.keySet());
		} else {
			chrNames.add(chrToWrite);
		}
		transcriptomeSpace = new TranscriptomeSpace(genesByChr);
		data = new AlignmentModel(bamFile, transcriptomeSpace, useFragments);
		initParams(firstPositionOnly, nor, data);
		isTranscriptomeSpace = true;
	}
	
	/**
	 * Construct with a genome space
	 * @param bamFile Bam alignments
	 * @param chrSizeFile Chromosome size file
	 * @param firstPositionOnly Only count the first position of each read
	 * @param useFragments Whether to convert reads to fragments
	 * @param nor Normalize by global amounts
	 */
	public WigWriter(String bamFile, String chrSizeFile, boolean firstPositionOnly, boolean useFragments, boolean nor) {
		this(bamFile, chrSizeFile, firstPositionOnly, useFragments, nor, null);
	}
	
	/**
	 * Construct with a genome space and only write one chromosome
	 * @param bamFile Bam alignments
	 * @param chrSizeFile Chromosome size file
	 * @param firstPositionOnly Only count the first position of each read
	 * @param useFragments Whether to convert reads to fragments
	 * @param nor Normalize by global amounts
	 * @param chrToWrite Only write this chromosome
	 */
	public WigWriter(String bamFile, String chrSizeFile, boolean firstPositionOnly, boolean useFragments, boolean nor, String chrToWrite) {
		genomeSpace = new GenomicSpace(chrSizeFile);
		chrNames = new TreeSet<String>();
		if(chrToWrite == null) {
			chrNames.addAll(genomeSpace.getReferenceNames());
		} else {
			chrNames.add(chrToWrite);
		}

		data = new AlignmentModel(bamFile, genomeSpace, useFragments);
		initParams(firstPositionOnly, nor, data);
		isTranscriptomeSpace = false;
	}
	
	/**
	 * Construct with either a genomic space or a transcriptome space
	 * @param bamFile Bam alignments
	 * @param geneBedFile Bed file of annotations for transcriptome space
	 * @param chrSizeFile File of chromosome sizes for genomic space
	 * @param firstPositionOnly Only count the first position of each read
	 * @param useFragments Whether to convert reads to fragments
	 * @param nor Normalize by global amounts
	 * @throws IOException
	 */
	public WigWriter(String bamFile, String geneBedFile, String chrSizeFile, boolean firstPositionOnly, boolean useFragments, boolean nor) throws IOException {
		this(bamFile, geneBedFile, chrSizeFile, firstPositionOnly, useFragments, nor, null);
	}
	
	/**
	 * Construct with either a genomic space or a transcriptome space and only write one chromosome
	 * @param bamFile Bam alignments
	 * @param geneBedFile Bed file of annotations for transcriptome space
	 * @param chrSizeFile File of chromosome sizes for genomic space
	 * @param firstPositionOnly Only count the first position of each read
	 * @param useFragments Whether to convert reads to fragments
	 * @param nor Normalize by global amounts
	 * @param chrToWrite Only write this chromosome
	 * @throws IOException
	 */
	public WigWriter(String bamFile, String geneBedFile, String chrSizeFile, boolean firstPositionOnly, boolean useFragments, boolean nor, String chrToWrite) throws IOException {
		if (geneBedFile == null && chrSizeFile == null) {
			throw new IllegalArgumentException("Choose one or both: gene bed file or chromosome size file");
		}
		if((geneBedFile != null && chrSizeFile != null)) {
			//logger.info("constructing using both bed and size files.");
			genomeSpace = new GenomicSpace(chrSizeFile);
			genesByChr = BEDFileParser.loadDataByChr(new File(geneBedFile));
			chrNames = new TreeSet<String>();
			if(chrToWrite == null) {
				chrNames.addAll(genesByChr.keySet());
			} else {
				chrNames.add(chrToWrite);
			}
			transcriptomeSpace = new TranscriptomeSpace(genesByChr);
			normData = new AlignmentModel(bamFile,transcriptomeSpace, useFragments);
			isTranscriptomeSpace = false;
			bothFiles = true;
			data = new AlignmentModel(bamFile, genomeSpace, useFragments);
			initParams(firstPositionOnly, nor, data);
			return;
			
		}
		
		if(geneBedFile != null) {
			//logger.info("Entered proper constructor for use of transcriptome space only");
			genesByChr = BEDFileParser.loadDataByChr(new File(geneBedFile));
			chrNames = new TreeSet<String>();
			if(chrToWrite == null) {
				chrNames.addAll(genesByChr.keySet());
			} else {
				chrNames.add(chrToWrite);
			}
			if (!firstPositionOnly) {
				TreeMap<String,Collection<Gene>> collGenesByChr = new TreeMap<String,Collection<Gene>>();
				Collection<Gene> collGenes;
				for (String chrom : chrNames) {
					logger.info("Collapsing genes for " + chrom);
					collGenes = collapseGenes(genesByChr.get(chrom));
					collGenesByChr.put(chrom, collGenes);
				}
				this.genesByChr = collGenesByChr;
			}
			transcriptomeSpace = new TranscriptomeSpace(genesByChr);
			data = new AlignmentModel(bamFile, transcriptomeSpace, useFragments);
			initParams(firstPositionOnly, nor, data);
			isTranscriptomeSpace = true;
			return;
		}
		if(chrSizeFile != null) {
			genesByChr = null;
			genomeSpace = new GenomicSpace(chrSizeFile);
			chrNames = new TreeSet<String>();
			if(chrToWrite == null) {
				chrNames.addAll(genomeSpace.getReferenceNames());
			} else {
				chrNames.add(chrToWrite);
			}
			data = new AlignmentModel(bamFile, genomeSpace, useFragments);
			initParams(firstPositionOnly, nor, data);
			isTranscriptomeSpace = false;
			return;
		}
	}
	
	private void initParams(boolean firstPositionOnly, boolean nor, AlignmentModel alignmentData) {
		readBeginningPositionOnly = firstPositionOnly;
		normalize = nor;
		if(normalize) normalization = new TranscriptAverageNormalization(alignmentData);
		else normalization = new RawCounts(alignmentData);
	}
	
	/**
	 * Add a read filter to alignment model
	 * @param filter The filter
	 */
	public void addReadFilter(Predicate<Alignment> filter) {
		data.addFilter(filter);
	}
	
	
	/**
	 * Get counts by position
	 * @param region The region
	 * @return Map of position to count, includes only positions with at least one read
	 * @throws IOException
	 */
	private Map<Integer, Double> getCounts(Annotation region) throws IOException {
		
		// Include entire read/fragment in transcriptome space
		if(!readBeginningPositionOnly && isTranscriptomeSpace) {
			return normalization.getNormalizedCountsByPosition(region);
		}
		
		// Include entire read/fragment in genomic space
		if(!readBeginningPositionOnly && !isTranscriptomeSpace) {
			
			if (bothFiles) {
				//logger.info("Using both files to write counts...");
				Collection<Gene> collapsedGenes = collapseGenes(genesByChr.get(region.getChr()));
				Iterator<Gene> winItr = collapsedGenes.iterator();
				Gene currGene;

				double geneCount;
				CountScore score;

				TreeMap<Integer,Double> rtrn = new TreeMap<Integer,Double>();
				while (winItr.hasNext()) {
					currGene = winItr.next();
					if (normalize) {
						geneCount = normData.getCount(currGene,true);
					} else {
						geneCount = currGene.getSize();
					}
					
					Iterator<? extends Annotation> exonItr = currGene.getExonSet().iterator();
					while (exonItr.hasNext()) {
					
						WindowScoreIterator<CountScore> wIter = data.scan(exonItr.next(), 1,0);
						Annotation a;
						int pos;
						double count;
						
						while (wIter.hasNext()){
							score = wIter.next();
							a = score.getAnnotation();
							pos = a.getStart();
							count = score.getCount();
							if (normalize) {
								if (count>0) {
									if (geneCount>0 && currGene.getSize()>0)
										rtrn.put(Integer.valueOf(pos), Double.valueOf(count/(geneCount/currGene.getSize()))); 
								}
							} else {
								if (count>0) {
									rtrn.put(Integer.valueOf(pos), Double.valueOf(count));
								}
							}
							score = null;
						}
					}
					
				}
				return rtrn;
			}
			//logger.info("Using chromosome size file to find counts...");
			return normalization.getNormalizedCountsByPosition(region);
		}
		
		// Count first read position only
		double norm = 1;
		if (normalize) {
			norm = data.getCount(region)/region.length();
		}
		CloseableIterator<Alignment> iter = data.getOverlappingReads(region, false);
		TreeMap<Integer, Double> rtrn = new TreeMap<Integer, Double>();
		while(iter.hasNext()) {
			Alignment read = iter.next();
			int firstPos = read.getFirstFragmentPositionStranded();
			read = null;
			Integer i = Integer.valueOf(firstPos);
			if(rtrn.containsKey(i)) {
				double oldVal = rtrn.get(i).doubleValue();
				double newVal = oldVal + 1;
				rtrn.put(i, Double.valueOf(newVal));
			} else {
				rtrn.put(i, Double.valueOf(1));
			}
			i = null;
		}
		iter.close();
		if (normalize) {
			for (Integer key : rtrn.keySet()) {
				double val = rtrn.get(key).doubleValue();
				rtrn.put(key, Double.valueOf(val/norm));
			}
		}
		return rtrn;
		
	}
	
	/**
	 * Get counts across a whole chromosome for positions in transcriptome space
	 * @param chr Chromosome name
	 * @return Map of position to count, includes only positions with at least one read
	 * @throws IOException
	 */
	private TreeMap<Integer, Double> getCountsInTranscriptomeSpace(String chr) throws IOException {
		if(!isTranscriptomeSpace) {
			throw new IllegalStateException("Must instantiate alignment model with a transcriptome space");
		}
		TreeMap<Integer, Double> rtrn = new TreeMap<Integer, Double>();
		
		for(Gene gene : genesByChr.get(chr)) {
			rtrn.putAll(getCounts(gene));
		}
		return rtrn;
	}
	
	/**
	 * Get counts across a whole chromosome
	 * @param chr Chromosome name
	 * @return Map of position to count, includes only positions with at least one read
	 * @throws IOException
	 */
	private Map<Integer, Double> getCountsInGenomeSpace(String chr) throws IOException {
		if(isTranscriptomeSpace) {
			throw new IllegalStateException("Must instantiate alignment model with a genome space");
		}
		return getCounts(genomeSpace.getEntireChromosome(chr));
	}
	
	/**
	 * Get the one based wig format position for a zero based coordinate
	 * @param zeroBasedCoordinate The zero based coordinate
	 * @return The wig position
	 */
	public static int coordinateToWigPosition(int zeroBasedCoordinate) {
		return zeroBasedCoordinate + 1;
	}
	 	
	/**
	 * Get the zero based coordinate for a wig position
	 * @param wigPosition The wig position
	 * @return The zero based coordinate
	 */
	public static int wigPositionToCoordinate(int wigPosition) {
		return wigPosition - 1;
	}
	
	/**
	 * Write a set of counts to an existing file writer
	 * @param w File writer
	 * @param chr Chromosome name
	 * @param counts Counts by position
	 * @throws IOException
	 */
	public static void write(FileWriter w, String chr, Map<Integer, Double> counts) throws IOException {
		w.write("variableStep chrom=" + chr + "\n");
		for(Integer i : counts.keySet()) {
			int pos = coordinateToWigPosition(i.intValue());
			w.write(pos + "\t" + counts.get(i).toString() + "\n");
		}
	}
	
	/**
	 * Write all counts to a wig file and convert to bigwig
	 * @param outFilePrefix Output file prefix for wig and bigwig
	 * @throws IOException
	 */
	public void writeFullWigAndBigwig(String outFilePrefix) throws IOException {
		String wigFile = outFilePrefix + ".wig";
		FileWriter w = new FileWriter(wigFile);
		for(String chrName : chrNames) {
			logger.info("Writing counts for chromosome " + chrName + "...");
			Map<Integer, Double> counts = new TreeMap<Integer, Double>();
			if(isTranscriptomeSpace) {
				if(genesByChr.get(chrName).isEmpty()) continue;
				counts = getCountsInTranscriptomeSpace(chrName);
			}
			else {
				counts = getCountsInGenomeSpace(chrName);
				// End position of chromosome is off the end - not valid position for wig format
				counts.remove(Integer.valueOf(Long.valueOf(genomeSpace.getLength(chrName)).intValue()));
			}
			write(w, chrName, counts);
		}
		w.close();
		logger.info("Done writing wig file.");

	}
	
	/**
	 * Collapse all overlapping genes within a collection into a non-overlapping set (considering strand)
	 * @param genes Collection of genes you wish to collapse
	 * @return Collection of collapsed genes
	 */
	private static Collection<Gene> collapseGenes(Collection<Gene> genes) {
		TreeSet<Gene> rtrn = new TreeSet<Gene>();
		TreeSet<Gene> tmpGenes = new TreeSet<Gene>();
		tmpGenes.addAll(genes);
		IntervalTree<Gene> tree = makeTree(genes);
		Collection<Gene> skip = new TreeSet<Gene>();
		String name;
		for (Gene rtrnGene : genes) {
			name = rtrnGene.getName();
			
			if (tmpGenes.contains(rtrnGene)) {
				skip.add(rtrnGene);
				Iterator<Node<Gene>> overItr = tree.overlappers(rtrnGene.getStart(),rtrnGene.getEnd());
				
				while (overItr.hasNext()) {
					Gene g2 = overItr.next().getValue();
					if (rtrnGene.overlapsStranded(g2)) {
						rtrnGene = rtrnGene.takeUnion(g2);
						//logger.info("taking union...");
						skip.add(g2);
					}
				}
				

				rtrnGene.setName(name);
				//logger.info("finished collapsing gene into: " + rtrnGene);
				rtrn.add(rtrnGene);
				tmpGenes.removeAll(skip);
				Iterator<Gene> skipItr = skip.iterator();
				while (skipItr.hasNext()) {
					Gene rmv = skipItr.next();
					tree.remove(rmv.getStart(),rmv.getEnd(),rmv);
				}
				skip.clear();
			}
		}
		return rtrn;
	}
	
	/**
	 * Creates an interval tree using a collection of genes; speeds up the collapsing process
	 * @param genes Collection of genes used to make the tree
	 * @return Finished interval tree
	 */
	private static IntervalTree<Gene> makeTree(Collection<Gene> genes) {
		IntervalTree<Gene> rtrn=new IntervalTree<Gene>();
		
		for(Gene gene: genes){
			int start=gene.getStart();
			int end=gene.getEnd();
			
			//if not already in then add
			Node<Gene> found=rtrn.find(start, end);
			if(found==null){
				rtrn.put(start, end, gene);
			}
			//else add isoform
			else{
				Gene ref=found.getValue();
				ref.addIsoform(gene);
				rtrn.put(start, end, ref);
			}
			
		}
		return rtrn;
	}
	
	private static CommandLineParser getCommandLineParser(String[] args) {
		
		CommandLineParser p = new CommandLineParser();
		p.addStringArg("-b", "Bam file", true);
		p.addStringArg("-g", "Gene bed file", false, null);
		p.addStringArg("-c", "Chromosome size file", false, null);
		p.addStringArg("-o", "Output file ending in .wig", true);
		p.addStringArg("-chr", "Single chromosome to write", false, null);

		p.addIntArg("-mf", "Max fragment length for paired reads", false, DEFAULT_MAX_FRAGMENT_LENGTH);
		p.addIntArg("-mg", "Max genomic span for paired reads", false, DEFAULT_MAX_GENOMIC_SPAN);
		p.addBooleanArg("-f", "Count beginning position of each read only", false, false);
		p.addBooleanArg("-pe", "Convert paired ends to fragments", false, DEFAULT_USE_FRAGMENTS);
		p.addBooleanArg("-n",  "Normalize position counts by average counts over region", false, false);
		
		p.parse(args);
		return p;
	}
	
	private static String getOutFileFromCommandArgs(String[] args) {
		CommandLineParser p = getCommandLineParser(args);
		return p.getStringArg("-o");
	}
	
	private static WigWriter buildFromCommandLine(String[] args) throws IOException {

		CommandLineParser p = getCommandLineParser(args);
		
		String bamFile = p.getStringArg("-b");
		String bedFile = p.getStringArg("-g");
		String chrSizeFile = p.getStringArg("-c");
		String singleChr = p.getStringArg("-chr");

		int maxFragmentLength = p.getIntArg("-mf");
		boolean fragments = p.getBooleanArg("-pe");
		int maxGenomicSpan = p.getIntArg("-mg");
		boolean firstPositionOnly = p.getBooleanArg("-f");
		boolean normalize = p.getBooleanArg("-n");
		
		WigWriter ww = new WigWriter(bamFile, bedFile, chrSizeFile, firstPositionOnly, fragments, normalize, singleChr);
		
		//ww.addReadFilter(new FragmentLengthFilter(ww.data.getCoordinateSpace(), maxFragmentLength));
		ww.addReadFilter(new GenomicSpanFilter(maxGenomicSpan));
		
		return ww; 
	}
	
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {

		WigWriter ww = buildFromCommandLine(args);
		ww.writeFullWigAndBigwig(getOutFileFromCommandArgs(args));
		
	}

}
