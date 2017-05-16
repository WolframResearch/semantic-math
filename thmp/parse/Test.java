package thmp.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableMultimap;

/**
 * Tests various functionalities
 * @author yihed
 *
 */
public class Test {

	public static void main(String[] args){
		List<String> words = new ArrayList<String>();
		words.add("ideals");
		
		List<String> transformed = TriggerMathObj.get_MathObj(words);
		for(String t : transformed)
			System.out.println(t);
		
		ImmutableMultimap.Builder<String, Integer> builder = new ImmutableMultimap.Builder<String, Integer>();
		builder.putAll("egg", Arrays.asList(1, 2, 3));
		ImmutableMultimap<String, Integer> map = builder.build();
		ImmutableMultimap<Integer, String> mapRev = map.inverse();
		System.out.println(mapRev);
		Integer i = 0, j = 0;
		Integer u = i == 1 ? 1 : null;
		Integer n = i == 1 ? 1 : 
			(j == 1 ? 1 : 0);
		//System.out.println(n);
		//System.out.println("$z$ hi".replaceAll("\\$([^$]+)\\$", "\"$1\""));
		//System.out.println("(2d(e)".replaceAll("[^(]*\\((^([\\(\\[\\{])*)\\]*((^([\\(\\[\\{]))*)\\).* ", "$1$2"));
		//System.out.println("(2d]e)".replaceAll("\\((^([\\(\\[\\{]*))\\]*((^([\\(\\[\\{]*)))\\).*", "$1$2"));
		//System.out.println("(2d]e)".replaceAll("\\(([^[^]]*)\\]([^[^]^)]*)\\).*", "$1"));
		String noParenClass = "[^\\(\\[\\{\\)\\]\\}]";
		System.out.println("fa(2d}e)".replaceAll(noParenClass+"*\\(([^\\[\\]]*)[\\]\\}]([^\\[\\]]*)\\).*", "($1$2)"));
		System.out.println("vc".replaceAll("[^ab](c)", "$1"));
		//System.out.println("aaazzz".matches("\\(([^()]|(?R))*\\)")); //false, need \\\\
		
	}
}
