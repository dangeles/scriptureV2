package nextgen.core.readFilters;

import nextgen.core.alignment.Alignment;
import nextgen.core.annotation.Annotation;

import org.apache.commons.collections15.Predicate;

public class SameOrientationFilter implements Predicate<Alignment> {
	
	private Annotation window;
	
	public SameOrientationFilter(Annotation w){
		window = w;
	}
	
	@Override
	public boolean evaluate(Alignment read) {
		
		if(read.getOrientation().equals(window.getOrientation()))
			return true;
		else
			return false;
	}
}