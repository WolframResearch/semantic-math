package thmp.test;

import org.junit.Test;

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
		System.out.println("replacedStr "+replacedStr);
		
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
		String commandStr = "\\In";
		String replacementStr = "\\mathrm{in}";
		int slotCount = 0;
		String thm = "hi \\In{content} there";
		String expected = "hi \\mathrm{in}{content} there";
		
		//System.out.println(parseMacros(commandStr, replacementStr, slotCount, thm));
		String actual = parseMacros(commandStr, replacementStr, slotCount, thm);
		
		Assert.assertTrue(expected.equals(actual));		
	}
	//\newcommand\ol[1]{\overline{#1}}
	
}
