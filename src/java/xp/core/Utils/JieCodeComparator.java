package xp.core.Utils;

import java.util.Comparator;


/**
 *  Created on 2013-3-7  
 */
public class JieCodeComparator 
	implements Comparator<JieCode>
	{
		@Override
		public int compare(JieCode a, JieCode b) {
			if (a.getTid()!=b.getTid())
			{
				return a.getTid()-b.getTid();
			}
			else
			{
				return a.getPos()-b.getPos();
			}
		}
		
	}
