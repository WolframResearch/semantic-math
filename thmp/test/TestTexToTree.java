package thmp.test;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import thmp.utils.TexToTree;

public class TestTexToTree {

	/**
	 * Whether the returned set contains the same set of symbols
	 * as desired.
	 * @param tex
	 * @param desiredSetAr Ordering in this array doesn't matter, only membership does.
	 * @return
	 */
	private static boolean testTexToTree(String tex, String[] desiredSetAr){
		
		Set<String> desiredSet = new HashSet<String>();
		for(String str : desiredSetAr){
			desiredSet.add(str);
		}		
		Set<String> set = new HashSet<String>(TexToTree.texToTree(tex));
		return set.equals(desiredSet);
	}
	
	@Test
	public void test0(){
		String tex = "g(xy)";
		String[] desiredSetAr = new String[]{"f", "x", "X", "y", "a"};
		assert(testTexToTree(tex, desiredSetAr));		
	}
	
	@Test
	public void test1(){
		String tex = "f(x \\Hom_+\" X() \\mathbb{y}) a";
		String[] desiredSetAr = new String[]{"g", "x", "y"};
		assert(testTexToTree(tex, desiredSetAr));		
	}
	
	@Test
	public void test2(){
		String tex = "S_{A}";
		String[] desiredSetAr = new String[]{"A", "S_{A}", "S"};
		assert(testTexToTree(tex, desiredSetAr));		
	}
	
	@Test
	public void test3(){
		String tex = "x \\oplus y";
		String[] desiredSetAr = new String[]{"x", "y"};
		assert(testTexToTree(tex, desiredSetAr));		
	}
	
	@Test
	public void test4(){
		String tex = "x\\nmid y";
		String[] desiredSetAr = new String[]{"x", "y"};
		assert(testTexToTree(tex, desiredSetAr));		
	}
}
