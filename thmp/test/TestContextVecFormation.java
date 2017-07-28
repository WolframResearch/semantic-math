package thmp.test;

import static org.junit.Assert.*;

import java.util.Map;
import org.junit.Test;

import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.search.CollectThm;
import thmp.utils.WordForms;
import thmp.utils.WordForms.WordMapIndexPair;

/**
 * Tests creation of context vectors
 * @author yihed
 *
 */
public class TestContextVecFormation {

	private static final Map<String, Integer> contextKeywordIndexQueryDict = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_INDEX_MAP();
	private static final boolean WRITE_UNKNOWN_WORDS_TO_FILE = false;
	
	private boolean checkContextVecMap(String input, String[][] desiredContextVec){
		
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		parseStateBuilder.setWriteUnknownWordsToFile(WRITE_UNKNOWN_WORDS_TO_FILE);
		boolean isVerbose = true;
		ParseState parseState = parseStateBuilder.build();
		ParseRun.parseInput(input, parseState, isVerbose);
		
		Map<Integer, Integer> combinedContextVecMap = parseState.getCurThmCombinedContextVecMap();
		for(String[] pair : desiredContextVec){
			Integer parentIndex = combinedContextVecMap.get(indexOf(pair[0]));
			if(null == parentIndex || parentIndex != indexOf(pair[1])){
				return false;
			}
		}
		return true;
	}
	
	@Test
	public void test1(){
		//this sentence makes zero sense. Sorry.
		String st = "field is global ring over group";
		//{1810=28, 6=27, 359=27, 27=1810, 28=-8}		
		String[][] childParentAr = new String[][]{{"is", "field"},
			{"ring", "is"},
			{"global", "ring"},
			{"group", "ring"},
		};
		assertTrue(checkContextVecMap(st, childParentAr));		
	}
	
	private int indexOf(String word){
		WordMapIndexPair pair = WordForms.uniformizeWordAndGetIndex(word, contextKeywordIndexQueryDict);
		assert pair != WordMapIndexPair.placeholderWordMapIndexPair();
		return pair.mapIndex();
	}
}
