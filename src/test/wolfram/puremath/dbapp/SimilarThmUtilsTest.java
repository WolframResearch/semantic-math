package test.wolfram.puremath.dbapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;
import org.junit.Test;

import com.wolfram.puremath.dbapp.SimilarThmUtils;

public class SimilarThmUtilsTest {

	private static final Random rand = new Random();
	
	private static void checkStringRep(List<Integer> thmIndexList) {
		String str = SimilarThmUtils.indexListToStr(thmIndexList);
		
		//System.out.println("index str: "+str);
		List<Integer> returnedIndex = SimilarThmUtils.strToIndexList(str);
		int thmIndexListSz = thmIndexList.size();
		for(int i = 0; i < thmIndexListSz; i++) {
			
			boolean sameQ = thmIndexList.get(i).intValue() == returnedIndex.get(i).intValue();
			if(!sameQ) {
				System.out.println(thmIndexList.get(i) + " not equal to " + returnedIndex.get(i) 
					+ " for list " + thmIndexList);
			}
			assertTrue(sameQ);
		}
	}
	
	@Test
	public void test1(){
		List<Integer> thmIndexList = new ArrayList<Integer>();
		//String inputStr = "locally convex space";
		//testSearch(inputStr);
		int upperBound = (int)Math.pow(2, 21);
		for(int i= 0; i  < 100; i++) {
			//thmIndexList.add(1000000);
			thmIndexList.add(rand.nextInt(upperBound));
		}
		checkStringRep(thmIndexList);
	}
	
}
