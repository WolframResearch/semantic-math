package thmp.utils;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

import exceptions.ParseRuntimeException;
import thmp.ParsedExpression;

/**
 * Utility functions pertaining to files.
 * 
 * @author yihed
 *
 */
public class FileUtils {

	//singleton, only one instance should exist
	private static volatile KernelLink ml;
	private static final Logger logger = LogManager.getLogger();
	
	/*random number used to keep track of version of serialized data, new random number 
	//is generated each time this class is loaded,
	//so oeffectively once per JVM session.
	//0.0001 chance that a different batch ends up with the same number. Can't be DESERIAL_VERSION_NUM_DEFAULT */
	private static final int SERIAL_VERSION_NUM = (int)Math.random()*10000+1;
	private static final int DESERIAL_VERSION_NUM_DEFAULT = 0;
	//intentionally not final, as needs to be set. Atomic, so to compare and update
	//atomically when multi-threaded.
	private static final AtomicInteger DESERIAL_VERSION_NUM = new AtomicInteger(DESERIAL_VERSION_NUM_DEFAULT);
	/*Should be set to true if currently generating data, */
	private static boolean dataGenerationModeBool;	
	//servletContext used when running from Tomcat
	private static ServletContext servletContext;
	private static final String RELATED_WORDS_MAP_SERIAL_FILE_STR = "src/thmp/data/relatedWordsMap.dat";
	
	/**
	 * Write content to file at absolute path.
	 * @param contentList
	 * @param fileTo
	 */
	public static void writeToFile(List<? extends CharSequence> contentList, Path fileToPath) {
		try {
			Files.write(fileToPath, contentList, Charset.forName("UTF-16"));
		} catch (IOException e) {
			e.printStackTrace();
			logger.error(e.getStackTrace());
		}
	}

	public static void setServletContext(ServletContext servletContext_){
		servletContext = servletContext_;
	}
	
	public static ServletContext getServletContext(){
		return servletContext;
	}
	
	/**
	 * Sets to dataGenerationMode. In this mode, don't need to wory about whether serialized data were
	 * generated from the same source, since only need to ensure consistency of output.
	 * e.g. when running DetectHypothesis.java.
	 */
	public static void set_dataGenerationMode(){
		dataGenerationModeBool = true;
	}
		
	/**
	 * Write content to file at absolute path.
	 * 
	 * @param contentList
	 * @param fileTo
	 */
	public static void writeToFile(Object obj, String fileToStr) {
		List<String> contentList = new ArrayList<String>();
		contentList.add(obj.toString());
		Path toPath = Paths.get(fileToStr);
		writeToFile(contentList, toPath);
	}
	
	/**
	 * Write content to file at absolute path.
	 * 
	 * @param contentList
	 * @param fileTo
	 */
	public static void writeToFile(List<? extends CharSequence> contentList, String fileToStr) {
		Path toPath = Paths.get(fileToStr);
		writeToFile(contentList, toPath);
	}
	
	/**
	 * Append to file, rather than overwrite.
	 */
	public static void appendObjToFile(Object obj, String pathToFile){
		
		boolean appendBool = true;
		try(FileWriter fw = new FileWriter(pathToFile, appendBool);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter outPrintWriter = new PrintWriter(bw))
			{
				outPrintWriter.println(obj);
				
			} catch (IOException e) {
			   logger.error("IOException while writing to unknown words file!");			   
			}		
	}
	
	/**
	 * Writes objects in iterable to the file specified by outputFileStr.
	 * @param list
	 * @param outputFileStr
	 */
	public static void serializeObjToFile(List<? extends Object> list, String outputFileStr){
		//serialize parsedExpressionList to persistent storage
		FileOutputStream fileOuputStream = null;
		ObjectOutputStream objectOutputStream = null;
		try{
			fileOuputStream = new FileOutputStream(outputFileStr);
			objectOutputStream = new ObjectOutputStream(fileOuputStream);
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("The output file " + outputFileStr + " cannot be found!");
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while opening ObjectOutputStream");
		}				
				//Iterator<? extends Object> iter = iterable.iterator();				
		try{
					/*while(iter.hasNext()){
						Object obj = iter.next();
						objectOutputStream.writeObject(obj);
					}*/
			objectOutputStream.writeObject(list);
			objectOutputStream.writeObject(SERIAL_VERSION_NUM);
					//System.out.println("parsedExpressionList: " + parsedExpressionList);
			objectOutputStream.close();
			fileOuputStream.close();
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while writing to file or closing resources");
		}
	}

	/**
	 * Deserialize objects from file supplied by serialFileStr.
	 * Note that this requires the DESERIAL_VERSION_NUM to equal that of previous 
	 * files deserialized in this JVM session. Don't call this if don't want to check
	 * for DESERIAL_VERSION_NUM.
	 * @param serialFileStr
	 * @return List of objects
	 */	
	public static Object deserializeListFromFile(String serialFileStr){
		
		FileInputStream fileInputStream = null;
		try{
			fileInputStream = new FileInputStream(serialFileStr);
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("Serialization data file not found!");
		}
		
		return deserializeListFromInputStream(fileInputStream, false);
	}

	public static Object deserializeListFromInputStream(InputStream inputStream) {
		return deserializeListFromInputStream(inputStream, false);
	}
	/**
	 * @param deserializedList
	 * @param fileInputStream
	 * @param checkVersion whether to check for DESERIAL_VERSION_NUM 
	 * @return
	 */
	public static Object deserializeListFromInputStream(InputStream inputStream, boolean checkVersion) {
		
		Object deserializedObj = null;	
		ObjectInputStream objectInputStream = null;		
		try{
			objectInputStream = new ObjectInputStream(inputStream);
		}catch(IOException e){
			silentClose(inputStream);
			e.printStackTrace();
			throw new IllegalStateException("IOException while opening ObjectOutputStream.");
		}
		
		try{
			deserializedObj = objectInputStream.readObject();
			if(checkVersion){
				int serialVersionInt = (int)objectInputStream.readObject();
				if(!dataGenerationModeBool && !DESERIAL_VERSION_NUM.compareAndSet(DESERIAL_VERSION_NUM_DEFAULT, serialVersionInt)){
					//DESERIAL_VERSION_NUM not 0, so already been set, thread-safe here,
					//since DESERIAL_VERSION_NUM can't be set unless 
					if(serialVersionInt != DESERIAL_VERSION_NUM.get()){
						String msg = "DESERIAL_VERSION_NUM inconsistent when deserializing! E.g. this will cause"
								+ "inconsistencies for data used for forming query and theorem pool context vectors.";
						logger.error(msg);
						throw new IllegalStateException(msg);
					}
					
				}/** else this must be first time deserializing in this JVM session, and so must
					have been set atomically just now. */				
			}
			//deserializedList = (List<? extends Object>)o;
			//System.out.println("object read: " + ((ParsedExpression)((List<?>)o).get(0)).getOriginalThmStr());			
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while reading deserialized data!");
		}catch(ClassNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("ClassNotFoundException while writing to file or closing resources.");
		}finally{
			try{
				objectInputStream.close();
				inputStream.close();
			}catch(IOException e){
				e.printStackTrace();
				throw new IllegalStateException("IOException while closing resources");
			}
		}
		return deserializedObj;
	}
	
	/**
	 * Closing resource, loggin possible IOException, without clobbering
	 * existing Exceptions if any has been thrown.
	 * @param fileInputStream
	 */
	public static void silentClose(Closeable resource){
		if(null == resource) return;
		try{
			resource.close();
		}catch(IOException e){
			e.printStackTrace();
			logger.error("IOException while closing resource: " + resource);
		}
	}
	
	/**
	 * Cleans up current JVM run session, such as closing any
	 * mathlink that got opened.
	 */
	public static void cleanupJVMSession(){
		closeKernelLinkInstance();
	}
	
	/**
	 * Closes the one running kernel link instance during this session.
	 * Must be run as part of cleaning up any session that uses a link.
	 */
	public static void closeKernelLinkInstance() {
		if(null != ml){
			synchronized(FileUtils.class){
				if(null != ml){
					ml.close();
					ml = null;
				}
			}
		}
	}
	
	/**
	 * Get KernelLink instance, 
	 * create one is none exists already.
	 * @return KernelLink instance.
	 */
	public static KernelLink getKernelLinkInstance() {
		//double-checked locking
		if(null == ml){
			//finer-grained locking than synchronizing whole method
			synchronized(FileUtils.class){
				//need to check again, in case ml was initialized while 
				//acquiring the lock.
				if(null == ml){
					ml = createKernelLink();
				}
			}			
		}	
		return ml;		
	}
	
	/**
	 * Creates the kernel link instance if none exists yet in this
	 * JVM session. Ensures only a single link instance is created, since 
	 * only one is needed.
	 * @return
	 */
	private static KernelLink createKernelLink() {

		String[] ARGV;
		
		String OS_name = System.getProperty("os.name");
		if (OS_name.equals("Mac OS X")) {
			ARGV = new String[] { "-linkmode", "launch", "-linkname",
					"\"/Applications/Mathematica2.app/Contents/MacOS/MathKernel\" -mathlink" };
		} else {
			// path on Linux VM (i.e. puremath.wolfram.com)
			// ARGV = new String[]{"-linkmode", "launch", "-linkname",
			// "\"/usr/local/Wolfram/Mathematica/11.0/Executables/MathKernel\"
			// -mathlink"};
			ARGV = new String[] { "-linkmode", "launch", "-linkname", "math -mathlink" };
		}
		try {
			ml = MathLinkFactory.createKernelLink(ARGV);
			String msg = "MathLink created! " + ml;
			System.out.println(msg);
			logger.info(msg);
			// discard initial pakets the kernel sends over.
			ml.discardAnswer();
		} catch (MathLinkException e) {
			e.printStackTrace();
		}
		return ml;
	}

	public static String getRELATED_WORDS_MAP_SERIAL_FILE_STR(){
		return RELATED_WORDS_MAP_SERIAL_FILE_STR;
	}
}
