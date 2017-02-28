package thmp.runner;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import thmp.ParsedExpression;
import thmp.TheoremContainer;
import thmp.search.CollectThm;
import thmp.search.Searcher;
import thmp.search.ThmSearch;
import thmp.search.TriggerMathThm2;
import thmp.utils.FileUtils;

/**
 * Generate SVD matrix in the form of sparse array. 
 * Useful when e.g. matrix generation in DetectHypothesis 
 * did not complete.
 * @author yihed
 *
 */
public class GenerateSVDMatrix{

	public static void main(String[] args){
		/*Deserialize parsedExpressionList from input */		
		int argsLen = args.length;
		if(argsLen < 1){
			System.out.println("Please supply a file to deserialize ParsedExpression's from!");
			return;
		}
		
		String fileStr = args[0];
		
		@SuppressWarnings("unchecked")
		List<ParsedExpression> parsedExpressionList = (List<ParsedExpression>)FileUtils.deserializeListFromFile(fileStr);
		thmp.utils.ResourceDeposit.setParsedExpressionList(parsedExpressionList);
		
		/*Do *NOT* set gatheringDataBoolToTrue()! Since need to use word maps gathered from last time.*/
		//Searcher.SearchMetaData.set_gatheringDataBoolToTrue();
		//FileUtils.set_dataGenerationMode();	
		
		ThmSearch.TermDocumentMatrix.createTermDocumentMatrixSVD(ImmutableList.<TheoremContainer>copyOf(parsedExpressionList));
		FileUtils.closeKernelLinkInstance();
	}
}
