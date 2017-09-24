package thmp.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.wolfram.jlink.Expr;

import thmp.exceptions.ParseRuntimeException.IllegalSyntaxException;
import thmp.parse.DetectHypothesis.Stats;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.parse.ThmP1.ParsedPair;
import thmp.utils.ExprUtils;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Collection of methods to run the parser.
 * Contains methods to parse verbosely or minimally without much output.
 * @author yihed
 */
public class ParseRun {

	private static final boolean DEBUG = FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;

	public static void main(String[] args){
		int argsLen = args.length;
		if(0 == argsLen){
			System.out.println("Supply a string to be parsed!");
			return;
		}
		StringBuilder sb = new StringBuilder(50);
		for(int i = 0; i < argsLen; i++){
			sb.append(args[i]).append(" ");
		}		
		Expr expr = parseInput(sb.toString());
		//don't hardcode address!
		List<Expr> exprList = new ArrayList<Expr>();
		exprList.add(expr);
		FileUtils.serializeObjToFile(exprList, "/Users/yihed/parseFromWL.dat");
	}
	
	/**
	 * Parse input String. 
	 * Does *not* clean up parseState, as the caller needs stateful parseState info.
	 * @param st
	 * @param parseState
	 * @param isVerbose whether to be verbose and print results to stdout.
	 */
	public static void parseInput(String st, ParseState parseState, boolean isVerbose){
		parseInput(st, parseState, isVerbose, null);
	}
	
	/**
	 * Parse input String. 
	 * @param st
	 */
	public static Expr parseInput(String st){
		
		boolean isVerbose = false;
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		final boolean WRITE_UNKNOWN_WORDS_TO_FILE = false;
		parseStateBuilder.setWriteUnknownWordsToFile(WRITE_UNKNOWN_WORDS_TO_FILE);		
		ParseState parseState = parseStateBuilder.build();
		
		parseInput(st, parseState, isVerbose, null);
		
		ParseStruct headParseStruct = parseState.getHeadParseStruct();
		List<Expr> exprList = new ArrayList<Expr>();
		StringBuilder sb = new StringBuilder(100);
		
		if(null != headParseStruct){
			headParseStruct.createStringAndRetrieveExpr(sb, exprList);
			if(!exprList.isEmpty()){
				return exprList.get(0);
			}
		}
		return ExprUtils.FAILED_EXPR();
	}
	
	/**
	 * Parse input String. 
	 * Does *not* clean up parseState, as the caller needs stateful parseState info.
	 * @param st
	 * @param parseState
	 * @param isVerbose whether to be verbose and print results to stdout.
	 * @param doPreprocess whether to run preprocess() step. E.g. don't run if parsing recipes.
	 */
	public static void parseInput(String st, ParseState parseState, boolean isVerbose, Stats stats, boolean...doPreprocess){
		
		List<Set<Integer>> relationalContextVecList = new ArrayList<Set<Integer>>();			
		parseState.resetNumNonTexTokens();
		
		String[] strAr;	
		if(doPreprocess.length > 0 && !doPreprocess[0]){
			strAr = new String[]{st};
		}else{
			strAr = ThmP1.preprocess(st);
		}
		
		for(int i = 0; i < strAr.length; i++){
			if(WordForms.getWhiteEmptySpacePattern().matcher(strAr[i]).find()){
				continue;
			}
			//alternate commenting out line to enable tex converter
			//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
			String curStrTrimmed = strAr[i].trim();
			try {
				parseState = ThmP1.tokenize(curStrTrimmed, parseState);
			} catch (IllegalSyntaxException e) {
				//Don't fill up logs with this, not helpful.
				//System.out.println("ParseRun - Input contains illegal syntax!");
				//e.printStackTrace();
				continue;
			}
			
			parseState = ThmP1.parse(parseState);
			
			Set<Integer> curContextVec = parseState.getRelationalContextVec();
			relationalContextVecList.add(curContextVec);
			//get context vector
			//if(isVerbose) System.out.println("cur vec: " + Arrays.toString(curContextVec));			
		}
		
		//combine the relational vecs
		parseState.setRelationalContextVec(relationalContextVecList);
		
		if(isVerbose) {
			ParseStruct headParseStruct = parseState.getHeadParseStruct();
			List<Expr> exprList = new ArrayList<Expr>();
			StringBuilder sb = new StringBuilder(100);
			
			if(null != headParseStruct){
				String headParseStructStr = headParseStruct.createStringAndRetrieveExpr(sb, exprList);
				System.out.println("@@@" + headParseStructStr);
				if(!exprList.isEmpty()){
					System.out.println("EXPR: \n" + exprList.get(0));
				}
			}else{
				System.out.println("@@@");
			}
			if(DEBUG){ 
				System.out.println("ParseRun - For str: " + st);
				Set<Integer> relationVec = parseState.getRelationalContextVec();
				System.out.println("Relational Vector Set size: " + (relationVec == null ? "vec null." : relationVec.size()));
				System.out.println("~++++~ CurThmCombinedContextVecMap: " +parseState.getCurThmCombinedContextVecMap());
			}
		}
		if(DEBUG){
			parseState.logState();
		}
		if(null != stats){
			ParseStruct head = parseState.getHeadParseStruct();
			if(null != head && !head.getWLCommandWrapperMMap().isEmpty()){
				stats.incrementHeadParseStructNullNum();
			}
			stats.incrementTotalThmsNum();
		}
		//combine these vectors together, only add subsequent vector entry
		//if that entry is 0 in all previous vectors int[].
		//int[] combinedVec = parseState.getCurThmCombinedContextVec();			
		//if(isVerbose) System.out.println("combinedVec: " + Arrays.toString(combinedVec));
		
		String parsedOutput = ThmP1.getAndClearParseStructMapList().toString();
		
		if(isVerbose) {
			System.out.println("PARTS: " + parsedOutput);			
			System.out.println("****ParsedExpr ");			
		}
		List<ParsedPair> parsedPairList = ThmP1.getAndClearParsedExpr();
		if(isVerbose){
			for(ParsedPair pair : parsedPairList){
				System.out.println(pair);
			}
		}
	}

}
