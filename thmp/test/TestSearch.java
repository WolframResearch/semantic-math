package thmp.test;


import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import thmp.Maps;
import thmp.ThmP1;
import thmp.ThmP1.ParsedPair;
import thmp.search.SearchCombined;
import thmp.search.SearchIntersection;
import thmp.search.ThmSearch;

/**
 * Test theorem search. Tests the theorem search by checking whether the top-ranked 
 * theorems consistently show up. Call SearchCombined.java
 * All strings, query or search sentences, should be lower-cased.
 * @author yihed
 */
public class TestSearch {

	private static final boolean CONTEXT_SEARCH_BOOL = false;
	@Before
	public void setUp() throws Exception {
		//Maps.buildMap();
		ThmSearch.initialize();
		System.out.println("Done initializing!");
	}
	
	public void testSearch(String str){
		testSearch(str, str);
	}
	
	public void testSearch(String inputStr, String desiredOutput){
		List<String> nearestThmsList = SearchCombined.searchCombined(inputStr, CONTEXT_SEARCH_BOOL);
		//List<Integer> nearestThmsList = SearchIntersection.getHighestThm(inputStr);
		assertTrue(nearestThmsList.size() > 1);
		String desiredOutputLower = desiredOutput.toLowerCase();
		assertTrue(nearestThmsList.get(0).toLowerCase().contains(desiredOutputLower) 
				|| nearestThmsList.get(1).toLowerCase().contains(desiredOutputLower));
	}
	
	@Test
	public void test1(){
		String inputStr = "locally convex space";
		testSearch(inputStr);
	}
	
	@Test
	public void test2(){
		String inputStr = "locally compact hausdorff space";
		testSearch(inputStr);
	}	
	
	@Test
	public void test3(){
		String inputStr = "every bounded linear surjection";
		testSearch(inputStr);
	}
	
	@Test
	public void test4(){
		String inputStr = "linear map between banach space";
		testSearch(inputStr);
	}
	
	@Test
	public void test5(){
		String inputStr = "closed unit ball of a normed linear space";
		testSearch(inputStr);
	}
	
	@Test
	public void test6(){
		String inputStr = "finite dimensional vector space";
		testSearch(inputStr);
	}
	
	@Test
	public void test7(){
		String inputStr = "Fredholm alternative";
		testSearch(inputStr);
	}
	
	@Test
	public void test8(){
		String inputStr = "direct sum of trivial complex";
		testSearch(inputStr);
	}
	
	@Test
	public void test9(){
		String inputStr = "localization of catenary ring";
		String desiredOutput = "catenary ring";
		testSearch(inputStr, desiredOutput);
	}
	
	@Test
	public void test10(){
		String inputStr = "projective dimension";
		testSearch(inputStr);
	}
	
}
