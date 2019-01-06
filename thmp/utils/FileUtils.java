package thmp.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tomcat.jdbc.pool.DataSource;

import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;
import com.wolfram.kernelserver.KernelPool;
import com.wolfram.kernelserver.KernelPoolException;
import com.wolfram.msp.servlet.MSPManager;
import com.wolfram.msp.servlet.MSPStatics;

import thmp.search.Searcher.SearchConfiguration;
import thmp.utils.MathLinkUtils.WLEvaluationMedium;
import thmp.utils.MathLinkUtils.WLEvaluationMedium.EvaluationMediumType;

/**
 * Utility functions pertaining to files.
 * 
 * @author yihed
 *
 */
public class FileUtils {

	//singleton, only one instance should exist
	private static volatile KernelLink ml;
	private static final Logger logger = LogManager.getLogger(FileUtils.class);
	private static final boolean IS_OS_X = "Mac OS X".equals(System.getProperty("os.name"));
	//whether parsing recipes.
	private static final boolean FOOD_PARSE;
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
	/**servletContext used when running from Tomcat. In current web container setting*/
	private static ServletContext servletContext;
	/*DataSource for current web container setting*/
	private static DataSource dataSource;
	
	private static final String KERNEL_POOL_NAME = "General";
	private static final String RELATED_WORDS_MAP_SERIAL_FILE_STR = "src/thmp/data/relatedWordsMap.dat";
	//get kernel pool, then acquire kernel instance from the pool.			
	private static MSPManager mspManager; //(MSPManager)servletContext.getAttribute(MSPStatics.MSP_MANAGER_ATTR);
	private static KernelPool kernelPool;	        
    public static final boolean isB67;
	
	public static final boolean SCRAPE_THM_NAME_Q = false;
	
	/** Do not introduce dependencies on other classes in static initializer. Since many classes
	 * count on this class as the lowest common denominator */
	static{
		//note this is fragile!! subject to change in byblis version.
		//need a way to differentiate byblis from puremath VM, both are amd64 Linux.
		String byblis67OsVersion = "2.6.32-642.11.1.el6.x86_64";
		boolean foodDefaultBool = false;
		if(byblis67OsVersion.equals(System.getProperty("os.version"))){
			//on byblis, for data processing. Differentiate between that
			//and Linux VM.
			foodDefaultBool = false;
			isB67 = true;
		}else {
			isB67 = false;
		}
		//change first one to adjust
		FOOD_PARSE = IS_OS_X ? true : foodDefaultBool;
	}
	/**
	 * Write content to file at absolute path.
	 * @param contentList
	 * @param fileToPath
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
	 * Read String from source file.
	 * Defaults to UTF-8 encoding, unless specified otherwise.
	 * @param filePath
	 * @param charset_
	 * @return
	 */
	public static String readStrFromFile(String filePath, Charset... charset_){
		StringBuilder sb = new StringBuilder(5000);
		Charset charset = charset_.length > 0 ? charset_[0] : Charset.forName("UTF-8");
		try {
			FileInputStream fileIS = null;
			FileReader fileReader = null;
			BufferedReader bReader = null;
			try{
				fileIS = new FileInputStream(filePath);
				InputStreamReader inputStreamReader = new InputStreamReader(fileIS, charset);
				bReader = new BufferedReader(inputStreamReader);
				
				String line;
				while((line = bReader.readLine()) != null){
					sb.append(line).append("\n");
				}					
			}finally{
				silentClose(fileIS);
				silentClose(bReader);
				silentClose(fileReader);
			}
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("FileNotFoundException while reading lines from file", e);
		} catch (IOException e) {
			throw new IllegalStateException("IOException while reading lines from file", e);
		}	
		return sb.toString();
	}
	
	/**
	 * Read lines from source files.
	 * Defaults to UTF-8 encoding, unless specified otherwise.
	 * @param fileName
	 * @param charset_
	 * @return list of lines read from src's
	 */
	public static List<String> readLinesFromFiles(List<String> fileNameList, Charset... charset_){
		List<String> lines = new ArrayList<String>();
		Charset charset = charset_.length > 0 ? charset_[0] : Charset.forName("UTF-8");
		for(String fileName : fileNameList){
			lines.addAll(readLinesFromFile(fileName, charset));
		}
		return lines;
	}
	
	public static List<String> readLinesFromFile(String fileName, Charset... charset_){
		List<String> lines = new ArrayList<String>();
		Charset charset = charset_.length > 0 ? charset_[0] : Charset.forName("UTF-8");
		
		try {
				FileInputStream fileIS = null;
				FileReader fileReader = null;
				BufferedReader bReader = null;
				try{
					fileIS = new FileInputStream(fileName);
					InputStreamReader inputStreamReader = new InputStreamReader(fileIS, charset);
					bReader = new BufferedReader(inputStreamReader);
					
					//fileReader = new FileReader(fileName);
					//bReader = new BufferedReader(fileReader);
					String line;
					while((line = bReader.readLine()) != null){
						lines.add(line);
					}					
				}finally{
					silentClose(fileIS);
					silentClose(bReader);
					silentClose(fileReader);
				}
			} catch (FileNotFoundException e) {
				throw new IllegalStateException("FileNotFoundException while reading lines from file", e);
			} catch (IOException e) {
				throw new IllegalStateException("IOException while reading lines from file", e);
			}		
		return lines;
	}
	
	/**
	 * Sets servlet context and datasource for the web container setting.
	 * This is called during servlet init.
	 * @param servletContext_
	 * @param dataSource
	 */
	public static void setServletContext(ServletContext servletContext_, DataSource dataSource_){
		servletContext = servletContext_;
		mspManager = (MSPManager)servletContext.getAttribute(MSPStatics.MSP_MANAGER_ATTR);
		kernelPool = mspManager.getKernelPool(KERNEL_POOL_NAME);
		dataSource = dataSource_;
		//See how many kernels and which ones.
		logger.info("setServletContext - kernelPool.getKernels(): " + kernelPool.getKernels());
		//IKernel kernel0=null;
		/*for(Map.Entry<String, IKernel> k : kernelPool.getKernels().entrySet()){
			kernel0 = k.getValue();
			break;
		}*/
		/*kernel0 = kernelPool.createKernel();
		logger.info("kernel0 created : " + kernel0);
		//IKernel kernel0 = kernelPool.getKernels().get(0);
		try {
			kernel0.initialize();
			kernelPool.registerKernel(kernel0);
			logger.info("kernel0.evaluate(1+2) "+kernel0.evaluate("1+2"));
		} catch (EvaluationException e) {
			logger.error("troubel evaluating");
			throw new IllegalStateException(e);
		}catch (Exception e) {
			logger.error("troubel registering and initializing");
			throw new IllegalStateException(e);
		}*/
	}
	
	public static ServletContext getServletContext(){
		return servletContext;
	}
	
	/**
	 * DataSource from web container, an implementation that generates pooled connections.
	 * Can get pooled Connections by calling method in DBUtils.java.
	 * @return
	 */
	public static javax.sql.DataSource getPooledDataSource(){
		return dataSource;
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
	 * Creates file with given name, if no such file exists.
	 * @param obj
	 * @param fileToStr
	 */
	public static void writeToFile(Object obj, String fileToStr) {
		createFileIfAbsent(fileToStr);
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
			   logger.error("appendObjToFile() - IOException while appending to file!");			   
			}		
	}
	
	/**
	 * Writes objects in iterable to the file specified by outputFileStr.
	 * Put objects that are not already List as first element in a List, but
	 * if obj is already a list, serialize that List (e.g. parsedExpressionList).
	 * @param list
	 * @param outputFileStr
	 */
	public static void serializeObjToFile(List<? extends Object> list, String outputFileStr){
		/*createFileIfAbsent(outputFileStr);*/
		FileOutputStream fileOuputStream = null;
		ObjectOutputStream objectOutputStream = null;
		try{
			fileOuputStream = new FileOutputStream(outputFileStr);			
		}catch(FileNotFoundException e){
			new File(findFilePathDirectory(outputFileStr)).mkdirs();
			try{
				fileOuputStream = new FileOutputStream(outputFileStr);
			}catch(FileNotFoundException e2){
				silentClose(fileOuputStream);
				throw new IllegalStateException("The output file " + outputFileStr + " cannot be found!");
			}
		}	
		try{
			objectOutputStream = new ObjectOutputStream(fileOuputStream);
		}catch(IOException e){
			silentClose(fileOuputStream);
			throw new IllegalStateException("IOException while opening ObjectOutputStream");
		}
		try{
			objectOutputStream.writeObject(list);
			objectOutputStream.writeObject(SERIAL_VERSION_NUM);
			//objectOutputStream.close();
			//fileOuputStream.close();
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while writing to file or closing resources");
		}finally {
			silentClose(fileOuputStream);
			silentClose(objectOutputStream);
		}
	}

	/**
	 * Atomic checking and file creation.
	 * @param outputFileStr
	 */
	private static void createFileIfAbsent(String outputFileStr) {
		File outputFile = new File(outputFileStr);
		try {
			//nothing is done if file already exists
			outputFile.createNewFile();
		} catch (IOException e1) {
			throw new IllegalStateException(e1);
		}
	}

	/**
	 * Deserialize objects from file supplied by serialFileStr.
	 * Note that this requires the DESERIAL_VERSION_NUM to equal that of previous 
	 * files deserialized in this JVM session (if enabled).
	 * **Don't forget to call FileUtils.getPathIfOnServlet(serialFileStr) on
	 * the input file String, if potentially on servlet!
	 * @param serialFileStr
	 * @return *List* of objects from the file.
	 */	
	public static Object deserializeListFromFile(String serialFileStr){
		//idempotent operation <--apparently not
		////serialFileStr = getPathIfOnServlet(serialFileStr);
		FileInputStream fileInputStream = null;
		try{
			fileInputStream = new FileInputStream(serialFileStr);
		}catch(FileNotFoundException e){
			String msg = "Serialization data file not found! " + serialFileStr;
			logger.error(msg);
			throw new IllegalStateException(msg);
		}
		return deserializeListFromInputStream(fileInputStream, false, serialFileStr);
	}

	public static Object deserializeListFromInputStream(InputStream inputStream) {
		return deserializeListFromInputStream(inputStream, false);
	}
	
	/**
	 * Returns the first object read from inputStream.
	 * @param fileInputStream
	 * @param checkVersion whether to check for DESERIAL_VERSION_NUM 
	 * @param serialFileStr optional parameter for trying with a custom objectInputStream,
	 * in case reading from the default one gives Exceptions, which will be thrown if serialFileStr 
	 * is not supplied.
	 * @return A *List* of items from the file.
	 */
	public static Object deserializeListFromInputStream(InputStream inputStream, boolean checkVersion,
			String... serialFileStr) {
		
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
			////logger.info("objectInputStream.class.getClassLoader(): "+objectInputStream.getClass().getClassLoader());
			//read first object in list
			try {
				deserializedObj = objectInputStream.readObject();
			}catch(ClassNotFoundException e){
				objectInputStream.close();				
				if(serialFileStr.length > 0) {
					//need the right class loader, in case class can't be resolved.
					objectInputStream = new ObjectInputStream(new FileInputStream(serialFileStr[0])) {					
						@Override
		                protected Class resolveClass(ObjectStreamClass objectStreamClass)
		                        throws IOException, ClassNotFoundException {
							return Class.forName(objectStreamClass.getName(), true, FileUtils.class.getClassLoader());	                    
		                }
		            };
					
		            try {
						deserializedObj = objectInputStream.readObject();						
		            }catch(ClassNotFoundException e2){
		            		objectInputStream.close();
		            		objectInputStream = new ObjectInputStream(new FileInputStream(serialFileStr[0])) {
			                @Override
			                protected Class resolveClass(ObjectStreamClass objectStreamClass)
			                        throws IOException, ClassNotFoundException {
			                		return Class.forName(objectStreamClass.getName(), true, objectStreamClass.forClass().getClassLoader());
			                }
			            };
			            deserializedObj = objectInputStream.readObject();	
		            }
				}else {
					throw e;
				}				
			}
			
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
			logger.info(e.getMessage());
			throw new IllegalStateException("IOException while reading deserialized data!", e);
		}catch(ClassNotFoundException e){
			throw new IllegalStateException("ClassNotFoundException while writing to file or closing resources. ClassLoader: "
					+ objectInputStream.getClass().getClassLoader(), e);
		}finally{
			silentClose(objectInputStream);
			silentClose(inputStream);
		}
		return deserializedObj;
	}
	
	/**
	 * Finds the directory component (root) in a file path. I.e. the component
	 * before the last File.separatorChar, e.g. '/'. If no slash found, 
	 * return the input String. So "a/b" part of e.g. a/b/c.dat"'
	 * @param filePath
	 * @return
	 */
	public static String findFilePathDirectory(String filePath){
		int len = filePath.length();
		int i;
		for(i = len-1; i > -1; i--){
			if(filePath.charAt(i) == File.separatorChar){
				break;
			}
		}
		if(i==-1){
			i = len;
		}
		return filePath.substring(0, i);
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
	 * create one is none exists already. Should only be called when not running on servlet
	 * @return KernelLink instance.
	 */
	public static KernelLink getKernelLinkInstance() {
		//put this condition back in place once kernel pool exception fixed!
		if(false && null != servletContext){
			throw new IllegalStateException("Should be getting kernel from kernel pool if running on servlet!");
		}
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
	 * Add trailing slash to path if not already present.
	 * @param path
	 * @return
	 */
	public static String addIfAbsentTrailingSlashToPath(String path){
		int pathLen = path.length();
		if(File.separatorChar != path.charAt(pathLen-1)){
			return path + File.separatorChar;
		}
		return path;
	}
	
	/**
	 * Get either kernel or link when running on servlet, depending on whether running on server.
	 */
	public static WLEvaluationMedium acquireWLEvaluationMedium(){
		
		WLEvaluationMedium medium = null;
		//**put this condition back in place once KernelPoolException resolved!
		if(//true || 
				null == servletContext){
			//running locally.
			return new WLEvaluationMedium(getKernelLinkInstance());
		}else{
			//running on servlet.
			assert null != kernelPool;
			//String msg = "acquireWLEvaluationMedium-trying to acquire kernel with stack trace: " 
			//		+ Arrays.deepToString(Thread.currentThread().getStackTrace());
			//logger.info(msg);
			try {
				medium = new WLEvaluationMedium(kernelPool.acquireKernel());
			} catch (KernelPoolException e) {
				//e.printStackTrace(); //HANDLE, see how Cloud deals with it. Fall back on link?? Not great
				throw new IllegalStateException("KernelPoolException when trying to acquire kernel: " 
						+ Arrays.toString(e.getStackTrace()) + " Original exception: " + e);
			} catch (InterruptedException e) {
				e.printStackTrace(); //HANDLE, should guarantee return is not null.
				throw new IllegalStateException("KernelPoolException when trying to acquire kernel: "+e.getMessage());
			}
	        return medium;
		}        
	}
	
	/**
	 * Releases kernel represented by medium instance back to kernel pool.
	 * Only does something if medium represents kernel from kernelpool.
	 * Does *not* close link if medium represents MathLink rather than kernel from pool.
	 * @param medium
	 */
	public static void releaseWLEvaluationMedium(WLEvaluationMedium medium){
		if(medium.evaluationMediumType == EvaluationMediumType.KERNEL){
			kernelPool.releaseKernel(medium.kernel());
		}		
		/* Don't close link if medium contains link! Since kernel will quit.
		 * else{
			KernelLink link = medium.link();
			if(null != link){
				link.close();
			}
			closeKernelLinkInstance();
		}*/
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
					"\"/Applications/Mathematica11_0_0.app/Contents/MacOS/MathKernel\" -mathlink" };
		} else {
			// path on Linux VM (i.e. puremath.wolfram.com)
			// ARGV = new String[]{"-linkmode", "launch", "-linkname",
			// "\"/usr/local/Wolfram/Mathematica/11.0/Executables/MathKernel\"
			// -mathlink"};
			String OS_version = System.getProperty("os.version");
			
			Path kernelPath = Paths.get("/Developer/Layouts/11.2.0/Executables/MathKernel");
			//need 11.3 for msc
			kernelPath = Paths.get("/MathKernel/11.3.0/Linux-x86-64/Secured/2017-12-18/Files/Executables/MathKernel");
			if(Files.exists(kernelPath)){ //e.g. on byblis67
				ARGV = new String[]{"-linkmode", "launch", "-linkname",
				"\"/Developer/Layouts/11.2.0/Executables/MathKernel\" -mathlink"};
				ARGV = new String[]{"-linkmode", "launch", "-linkname",
				"\"/MathKernel/11.3.0/Linux-x86-64/Secured/2017-12-18/Files/Executables/MathKernel\" -mathlink"};
			}
			/*if(OS_version.equals("2.6.32-696.3.1.el6.x86_64")){
				ARGV = new String[]{"-linkmode", "launch", "-linkname",
					"\"/Developer/Layouts/11.1.1/Executables/MathKernel\" -mathlink"};
			}*/
			else if("3.10.0-514.16.1.el7.x86_64".equals(OS_version)){
				//on puremath VM, right now this is used for debugging kernel aquisition
				ARGV = new String[]{"-linkmode", "launch", "-linkname",
					"\"/usr/local/Wolfram/Mathematica/11.1/Executables/MathKernel\" -mathlink"};
			}else{
				ARGV = new String[] { "-linkmode", "launch", "-linkname", "math -mathlink" };
			}
			///e.g. Developer/Layouts/11.3/Executables
			logger.info("Launching kernel with path: " +Arrays.toString(ARGV));
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

	/**
	 * Execute the given command.
	 * @param cmd
	 */
	public static void runtimeExec(String cmd){
		Runtime rt = Runtime.getRuntime();
		try {
			rt.exec(cmd);
		} catch (IOException e) {
			String msg = "Execution failed for command " + cmd;
			System.out.println(msg);
			logger.error(msg);
		}
	}
	
	public static String getRELATED_WORDS_MAP_SERIAL_FILE_STR(){
		return RELATED_WORDS_MAP_SERIAL_FILE_STR;
	}
	
	/**
	 * Waits for process to complete, and prints output.
	 * Should only be used locally! Because unnecessary IO if on server.
	 * @param pr
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void waitAndPrintProcess(Process pr) throws IOException, InterruptedException {
		InputStream inputStream = pr.getInputStream();
		InputStream errorStream = pr.getErrorStream();
		InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
		BufferedReader inputReader = new BufferedReader(inputStreamReader);		
		BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
		String line;
		while(null != (line = inputReader.readLine())){
			//System.out.println(new String(byteAr, Charset.forName("UTF-8")));
			System.out.println(line);
		}
		while(null != (line = errorReader.readLine())){
			//System.out.println(new String(byteAr, Charset.forName("UTF-8")));
			System.out.println(line);
		}
		pr.waitFor();
		FileUtils.silentClose(inputReader);
		FileUtils.silentClose(errorReader);
	}
	
	/**
	 * Deserialize meta data
	 * @return
	 */
	public static SearchConfiguration deserializeSearchConfiguration(){
		//String path = metaDataFilePath;
		String path = SearchConfiguration.searchConfigurationSerialPath();
		if(null != servletContext){
			path = servletContext.getRealPath(path);
		}
		@SuppressWarnings("unchecked")
		SearchConfiguration searchConfig 
			= ((List<SearchConfiguration>)FileUtils.deserializeListFromFile(path)).get(0);
		return searchConfig;
	}
	
	/**
	 * Retrieves path to resource pointed to by path, 
	 * taking into account whether 
	 * currently running in servlet environment.
	 * Idempotent operation.
	 * @param path
	 * @return
	 */
	public static String getPathIfOnServlet(String path){
		if(null != servletContext){
			return servletContext.getRealPath(path);
		}else{
			return path;
		}
	}
	
	/**
	 * Whether the OS is MacOS X 
	 * @return
	 */
	public static boolean isOSX(){
		return IS_OS_X;
	}
	
	/**
	 * Whether currently parsing recipes. Affects tokenization, etc.
	 * @return
	 */
	public static boolean isFoodParse(){
		return FOOD_PARSE;
	}
}
