package thmp.test;

import org.junit.Test;

import thmp.utils.MacrosTrie;
import thmp.utils.MacrosTrie.MacrosTrieBuilder;

public class TestMacrosTrie {

	public static void main(String[] args) {
		parseMacros();
	}
	
	private static String parseMacros() {
		
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

		String commandStr = "\\In";
		String replacementStr = "\\mathrm{in}\\,";
		//String slotCountStr = newThmMatcher.group(2);
		int slotCount = 0;
		macrosTrieBuilder.addTrieNode(commandStr, replacementStr, slotCount);
		
		MacrosTrie macrosTrie = macrosTrieBuilder.build();
		String replacedStr = macrosTrie.replaceMacrosInThmStr("hi \\In{content} there");
		System.out.println("replacedStr "+replacedStr);
		
		return replacedStr;
	}
	
	@Test
	public void test1() {
		String commandStr = "\\wh";
		String replacementStr = "\\widehat{#1}";
		
	}

}
