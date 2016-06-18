package thmp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class StructH<H> extends Struct{

	private HashMap<String, String> struct; //hashmap
	//ent (entity) is only structure that uses hashmap
	private String type; //ent, adj, etc
	private boolean hasChild = false;
	private ArrayList<Struct> children; 
	//relation to child, eg "of," "enjoyed"
	private ArrayList<String> childRelation;	
	private double score;
	private double maxDownPathScore;
	private StructList structList;
	
	//parent
	//private Struct parent;
	
	public StructH(HashMap<String, String> struct, String type){
	
		this.struct = struct;
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelation = new ArrayList<String>();
		this.score = 1;
	}
	
	public StructH(HashMap<String, String> struct, String type, StructList structList){
		
		this.struct = struct;
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelation = new ArrayList<String>();
		this.score = 1;
		this.structList = structList;
	}

	//when is this used??
	public StructH(String type){
		
		this.type = type;
		this.children = new ArrayList<Struct>();
		this.childRelation = new ArrayList<String>();
		this.score = 1;
	}
	
	public void set_struct(HashMap<String, String> struct){
		this.struct = struct;
	}

	@Override
	public void set_structList(StructList structList){
		this.structList = structList;
	}
	
	@Override
	public StructList StructList(){
		return this.structList;
	}
	
	@Override
	public void set_maxDownPathScore(double pathScore){
		this.maxDownPathScore = pathScore;
	}
	
	@Override
	public double maxDownPathScore(){
		return this.maxDownPathScore;
	}
	
	@Override
	public double score(){
		return this.score;
	}
	
	@Override
	public void set_score(double score){
		this.score = score;
	}
	
	//make deep copy, struct and children children are copied
	@Override
	public StructH<H> copy(){
		HashMap<String, String> structCopy = new HashMap<String, String>(this.struct);
		StructH<H> newStructH = new StructH<H>(structCopy, this.type, this.structList);
		
		for(int i = 0; i < this.children.size(); i++){
			newStructH.add_child(this.children.get(i), this.childRelation.get(i));
		}
		
		return newStructH;
	}
	
	public void add_previous(Struct prev){
		hasChild = true;
		children.add(prev);
		//if no relation specified
		childRelation.add("");
	}

	@Override
	public void add_child(Struct child, String relation){
		hasChild = true;
		children.add(child);
		childRelation.add(relation);
	}
	
	public boolean has_previous(){
		return hasChild;		
	}
	
	@Override
	public ArrayList<Struct> children(){
		return children;		
	}
	
	@Override
	public ArrayList<String> childRelation(){
		return childRelation;		
	}
	
	@Override
	public HashMap<String, String> struct(){		
		return this.struct;		
	}
	
	@Override
	public String type(){
		return this.type;		
	}
	
	@Override
	public void set_type(String type){
		this.type = type;		
	}
	
	@Override
	public String toString(){
		String str = this.type;
		str += this.struct; //struct is hashmap for structH
		
		return str;
	}
	
	//similar to toString(). Presents StructH as a String
	@Override
	public String present(String str){
		str += this.type.equals("ent") ? "MathObj" : this.type;
		str += "{";
		Iterator<Entry<String, String>> structIter = struct.entrySet().iterator();
		String name = "", called = "", ppt = "", tex = "";
		
		while(structIter.hasNext()){
			Entry<String, String> entry = structIter.next();
			if(entry.getValue().matches("ppt") ){
				ppt += entry.getKey() + ", ";
			}
			else if(entry.getKey().matches("name") ){
				name = entry.getValue();
			}
			else if(entry.getKey().matches("called") ){
				called = entry.getValue();
			}
			else if(entry.getKey().matches("tex") ){
				tex = entry.getValue();
			}
		}		
		
		name = tex.length() > 0 ? name + ", ": name;
		tex = called.length() > 0 ? tex + ", ": tex;
		called = !(ppt.length() == 0) ? called + ", " : called;
		ppt = ppt.length() > 2 ? ppt.substring(0, ppt.length() - 2) : ppt;		
		
		str += name + tex + called + ppt;
		
		if(children.size() > 0) str += ", ";
		
		//iterate through children		
		int childrenSize = children.size();
		for(int i = 0; i < childrenSize; i++){
			str += childRelation.get(i) + " ";
			Struct child = children.get(i);
			str = child.present(str);	
			if(i < childrenSize - 1)
				str += ", ";
		}
		
		str += "}";

		return str;
	}
	
	public void test(Object obj){
		
	}

}
