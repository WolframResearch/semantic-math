package thmp.parse;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import syntaxnet.SentenceOuterClass;
import syntaxnet.SentenceOuterClass.Sentence;

/**
 * Utility class used for querying syntaxnet model, getting the output,
 * and parse that output.
 * @author yihed
 *
 */
public class SyntaxnetQuery {

	//in protobuf v3, can no longer explicitly specify default value, which is 0 here,
	//but -1 would have been better.
	private static final int DEFAULT_HEAD_INDEX = 0;
	private static final Logger logger = LogManager.getLogger(DetectHypothesis.class);
	//sentence deserialized from shell script.
	private Sentence sentence;
	//make system indepdentn!! Need to configure paths on different machines!
	private static final String pathToScript = "/Users/yihed/models/syntaxnet";
	//make singleton placeholder sentence
	private static final Sentence PLACEHOLDER_SENTENCE;
	
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
			//sanitize input!!
			rt.exec("echo \"" + input + "\" | " + pathToScript + "/demo.sh");
		} catch (IOException e) {
			String msg = " " + Arrays.toString(e.getStackTrace());
			logger.error(msg);
			this.sentence = PLACEHOLDER_SENTENCE;
			return;
		}
		//get file containing serialized string
		InputStream fileInputStream;
		
		/*fileInputStream = new FileInputStream("src/thmp/data/serializedSentence.txt");		
		this.sentence = Sentence.parseFrom(fileInputStream);*/		
		
	}
	
	/**
	 * 
	 * @param tokenStr
	 * @param targetIndex index of     in original string,   could vary
	 * from syntaxnet's tokenization.
	 * @return
	 * 	 * return default head if token not found, or is the head.
	 */
	private int getTokenHead(String tokenStr, int targetIndex){
		
		//check if contains field?? rather than relyin on defaults
		
		
		return 0;	
	}
	
	
	/* Token example, can use getters for all these:
	 *word: "over"
start: 27
end: 30
head: 4
tag: "IN"
category: "ADP"
label: "prep" 
	 */

	public static void main(String[] args) throws FileNotFoundException, IOException{
		
		SentenceOuterClass.Sentence c = SentenceOuterClass.Sentence.parseFrom(new FileInputStream("src/thmp/data/serializedSentence.txt"));
		System.out.println(c.getTokenList().get(4).getHead());
		/*for(SentenceOuterClass.Token token : c.getTokenList()){
			System.out.println("Another token: ");
			System.out.println(token);
			
		}*/
		Sentence.Builder b = Sentence.newBuilder();
		Sentence s = b.build();
		System.out.println(s.getTokenList());
	}	

}
