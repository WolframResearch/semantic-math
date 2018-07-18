package thmp.test;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import thmp.utils.MacrosTrie;
import thmp.utils.MacrosTrie.MacrosTrieBuilder;

public class TestMacrosTrie {

	/*public static void main(String[] args) {
		parseMacros();
	}*/
	
	private static String parseMacros(String commandStr, String replacementStr, int slotCount,
			String thm) {
		
		MacrosTrieBuilder macrosTrieBuilder = new MacrosTrieBuilder();
		/*
		 * \newcommand\frk[1]{\mathfrak{#1}}
		\newcommand\mr[1]{\mathrm{#1}}
		\newcommand\ol[1]{\overline{#1}}
		\newcommand\wh[1]{\widehat{#1}}
		\newcommand\mbf[1]{\mathbf{#1}}
		\newcommand\MUBT{\omega}
		\newcommand{\In}{\mathrm{in}\,}
		\newcommand\m{\mathrm{m}}
		 */
		
		//String slotCountStr = newThmMatcher.group(2);
		
		macrosTrieBuilder.addTrieNode(commandStr, replacementStr, slotCount);
		
		MacrosTrie macrosTrie = macrosTrieBuilder.build();
		String replacedStr = macrosTrie.replaceMacrosInThmStr(thm);
		//System.out.println("replacedStr "+replacedStr);
		
		return replacedStr;
	}
	
	/**
	 * Takes lists of command and replacements strings, when multiple macros are needed.
	 * @param commandStrList
	 * @param replacementStrList
	 * @param slotCount
	 * @param thm
	 * @return
	 */
	private static String parseMacros(List<String> commandStrList, List<String> replacementStrList, 
			List<Integer> slotCountList, String thm) {
		
		MacrosTrieBuilder macrosTrieBuilder = new MacrosTrieBuilder();
		
		int listLen = commandStrList.size();
		
		for(int i = 0; i < listLen; i++) {
			String commandStr = commandStrList.get(i);
			String replacementStr = replacementStrList.get(i);
			int slotCount = slotCountList.get(i);
			macrosTrieBuilder.addTrieNode(commandStr, replacementStr, slotCount);
		}
		
		MacrosTrie macrosTrie = macrosTrieBuilder.build();
		String replacedStr = macrosTrie.replaceMacrosInThmStr(thm);
		
		return replacedStr;
	}
	
	@Test
	public void test1() {
		String commandStr = "\\wh";
		String replacementStr = "\\widehat{#1}";
		int slotCount = 1;
		String thm = "a \\wh {b}";
		String expected = "a \\widehat{b}";
		
		//System.out.println(parseMacros(commandStr, replacementStr, slotCount, thm));
		
		String actual = parseMacros(commandStr, replacementStr, slotCount, thm);
		Assert.assertTrue(expected.equals(actual));
	}

	@Test
	public void test2() {
		String commandStr = "\\In";
		String replacementStr = "\\mathrm{in}";
		int slotCount = 0;
		String thm = "hi \\In{content} there";
		String expected = "hi \\mathrm{in}{content} there";
		
		//System.out.println(parseMacros(commandStr, replacementStr, slotCount, thm));
		String actual = parseMacros(commandStr, replacementStr, slotCount, thm);
		
		Assert.assertTrue(expected.equals(actual));		
	}

	@Test
	public void test3() {
		//\\newcommand{\\Xb}{\\textbf{\\upshape X}}
		String commandStr = "\\Xb";
		String replacementStr = "\\textbf{\\upshape X}";
		int slotCount = 0;
		String thm = "this is \\Xb";
		String expected = "this is \\textbf{\\upshape X}";
		
		String actual = parseMacros(commandStr, replacementStr, slotCount, thm);
		
		Assert.assertTrue(expected.equals(actual));		
	}
	
	@Test
	public void test4() {
		////\newcommand\ol[1]{\overline{#1}}
		String commandStr = "\\ol";
		String replacementStr = "\\overline{#1}";
		int slotCount = 1;
		String thm = "hi \\ol{3} ";
		String expected = "hi \\overline{3} ";
		
		String actual = parseMacros(commandStr, replacementStr, slotCount, thm);
		boolean isEqual = expected.equals(actual);
		if(!isEqual) {
			System.out.println("Wrong result! Expected: " + expected + " Actual: " + actual);
		}
		Assert.assertTrue(isEqual);		
	}
	
	@Test
	public void testNested1() {
		/*
		 * \newcommand{\spc}{sp}
			\DeclareMathOperator{\ess}{ess}			
			\newcommand{\speess}{\spc_{\eps,\ess}}
			\speess A
		 */
		List<String> commandStrList = new ArrayList<String>();
		List<String> replacementStrList = new ArrayList<String>();		
		List<Integer> slotCountList = new ArrayList<Integer>();
		
		commandStrList.add("\\spc");
		replacementStrList.add("sp");
		slotCountList.add(0);
		
		commandStrList.add("\\ess");
		replacementStrList.add("ess");
		slotCountList.add(0);
		
		commandStrList.add("\\spes");
		replacementStrList.add("\\spc_{\\ess}");
		slotCountList.add(0);
		
		String thm = "\\spes";
		String expected = "sp_{ess}";
		
		String actual = parseMacros(commandStrList, replacementStrList, slotCountList, thm);
		boolean isEqual = expected.equals(actual);
		if(!isEqual) {
			System.out.println("Wrong result! Expected: " + expected + " Actual: " + actual);
		}
		Assert.assertTrue(isEqual);		
	}
	
	//test begin theorem start and end environment macros
	//\begin{pro1}
	
}
