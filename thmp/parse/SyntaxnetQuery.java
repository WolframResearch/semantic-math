package thmp.parse;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import syntaxnet.SentenceOuterClass;
import syntaxnet.SentenceOuterClass.Sentence;
import syntaxnet.SentenceOuterClass.Token;
import thmp.utils.FileUtils;

/**
 * Utility class used for querying syntaxnet model, getting the output,
 * and parse that output.
 * @author yihed
 *
 */
public class SyntaxnetQuery {

	//in protobuf v3, can no longer explicitly specify default value, which is 0 here,
	//but -1 would have been better.
	private static final int DEFAULT_HEAD_INDEX = -1;
	private static final Logger logger = LogManager.getLogger(DetectHypothesis.class);
	//sentence deserialized from shell script.
	private Sentence sentence;
	//make system indepdentn!! Need to configure paths on different machines!
	//private static final String pathToScript = "/Users/yihed/models/syntaxnet";
	//make singleton placeholder sentence
	private static final Sentence PLACEHOLDER_SENTENCE;
	//private int errorCode ;
	
	static{
		Sentence.Builder b = Sentence.newBuilder();
		PLACEHOLDER_SENTENCE = b.build();
	}
	
	/**
	 * Should only call once per input!
	 * @param input Input string to parse.
	 */
	public SyntaxnetQuery(String input){
		//call script to evaluate input
		
		Runtime rt = Runtime.getRuntime();
		try {
			//sanitize input! turn into array form!
			//"echo \"" + input + "\" | " + pathToScript + "/demo.sh"
			//Process process = rt.exec(pathToScript + "/demo.sh");
			Process process = rt.exec(new String[]{"src/test1.sh", input});
			/*OutputStream outputStream = process.getOutputStream();
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, Charset.forName("UTF-8")));			
			bufferedWriter.write(input);*/
			FileUtils.waitAndPrintProcess(process);
			
		} catch (IOException e) {
			String msg = "IOException when executing  " + Arrays.toString(e.getStackTrace());
			/*handleParseError(msg);
			return;*/
			throw new IllegalStateException(e);
		} catch (InterruptedException e) {
			String msg = "InterruptedException when executing  " + Arrays.toString(e.getStackTrace());
			handleParseError(msg);
			return;
		}
		//get file containing serialized string
		InputStream fileInputStream = null;		
		try {
			fileInputStream = new FileInputStream("src/thmp/data/serializedSentence.txt");
		} catch (FileNotFoundException e) {
			String msg = "FileNotFoundException when  ....  " + Arrays.toString(e.getStackTrace());
			handleParseError(msg);
			return;
		}		
		try {
			this.sentence = Sentence.parseFrom(fileInputStream);
		} catch (IOException e) {
			String msg = "IOException when parsing  " + Arrays.toString(e.getStackTrace());
			handleParseError(msg);
			return;
		}	
		//System.out.println("SyntaxnetQuery-sentence: " + sentence);
	}

	/**
	 * @param msg
	 */
	private void handleParseError(String msg) {
		logger.error(msg);
		System.out.println(msg);
		this.sentence = PLACEHOLDER_SENTENCE;
	}
	
	/**
	 * 
	 * @param    start and end indices of word in original sentence
	 * @param tokenStr
	 * @param targetIndex index of     in original string,   could vary
	 * from syntaxnet's tokenization.
	 * @return
	 * 	 * return default head if token not found, or is the head.
	 */
	public int getTokenHead(String tokenStr, int targetIndex//, int wordStartIndex, int wordEndIndex
			){
		/* Token example, can use getters for all these:
		 *word: "over"
	start: 27
	end: 30
	head: 4
	tag: "IN"
	category: "ADP"
	label: "prep" 
		 */
		List<Token> tokenList = this.sentence.getTokenList();
		int tokenListSz = tokenList.size();
		int headIndex = DEFAULT_HEAD_INDEX;
		
		if(targetIndex > tokenListSz){
			return DEFAULT_HEAD_INDEX;
		}

		//List<Token> tokenList = this.sentence.getTokenList();
		//look left, right
		if(targetIndex < tokenListSz){
			headIndex = getTokenHeadHelper(tokenStr, tokenList, headIndex, targetIndex);			
		}else if(targetIndex == tokenListSz){//must be that targetIndex == tokenListSz
			int index = targetIndex-1;
			headIndex = getTokenHeadHelper(tokenStr, tokenList, headIndex, index);	
		}
		
		return headIndex;
	}

	/**
	 * @param tokenStr
	 * @param tokenList
	 * @param headIndex
	 * @param index
	 * @return
	 */
	private int getTokenHeadHelper(String tokenStr, List<Token> tokenList, int headIndex, int index) {
		Token token = tokenList.get(index);
		String word = token.getWord();			
		if(tokenStr.equals(word)){
			headIndex = token.getHead();
		}else if(tokenStr.equals(tokenList.get(index-1))){ //look left
			headIndex = token.getHead();
		}
		return headIndex;
	}

	public static void main(String[] args) throws FileNotFoundException, IOException{
		
		//SentenceOuterClass.Sentence c = SentenceOuterClass.Sentence.parseFrom(new FileInputStream("src/thmp/data/serializedSentence.txt"));
		//System.out.println(c.getTokenList().get(4).getHead());
		/*for(SentenceOuterClass.Token token : c.getTokenList()){
			System.out.println("Another token: ");
			System.out.println(token);
			
		}*/
		/*Sentence.Builder b = Sentence.newBuilder();
		Sentence s = b.build();
		System.out.println(s.getTokenList());*/
		SyntaxnetQuery query = new SyntaxnetQuery("this is a pipe");
		Sentence s = query.sentence;
		//System.out.println("Sentence token list: " + s.getTokenList());
	}	

}
