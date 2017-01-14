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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;

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
	/**
	 * Write content to file at absolute path.
	 * 
	 * @param contentList
	 * @param fileTo
	 */
	public static void writeToFile(List<? extends CharSequence> contentList, Path fileToPath) {
		try {
			Files.write(fileToPath, contentList, Charset.forName("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
			logger.error(e.getStackTrace());
		}
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
	 * @param iterable
	 * @param outputFileStr
	 */
	public static void serializeObjToFile(Iterable<? extends Object> iterable, String outputFileStr){
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
				
				Iterator<? extends Object> iter = iterable.iterator();				
				try{
					while(iter.hasNext()){
						Object obj = iter.next();
						objectOutputStream.writeObject(obj);		
					}
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
		
		return deserializeListFromInputStream(fileInputStream);
	}

	/**
	 * @param deserializedList
	 * @param fileInputStream
	 * @return
	 */
	public static Object deserializeListFromInputStream(InputStream inputStream) {
		
		Object deserializedList = null;	
		ObjectInputStream objectInputStream = null;		
		try{
			objectInputStream = new ObjectInputStream(inputStream);
		}catch(IOException e){
			silentClose(inputStream);
			e.printStackTrace();
			throw new IllegalStateException("IOException while opening ObjectOutputStream.");
		}
		
		try{
			deserializedList = objectInputStream.readObject();
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
		return deserializedList;
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
			System.out.println("MathLink created! " + ml);
			// discard initial pakets the kernel sends over.
			ml.discardAnswer();
		} catch (MathLinkException e) {
			e.printStackTrace();
		}
		return ml;
	}

}
