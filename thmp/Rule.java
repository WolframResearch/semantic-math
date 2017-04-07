package thmp;
/**
 * Rule instances connect different parts of speech,
 * with corresponding probabilities
 * 
 * @author yihed
 */

public class Rule {
	//relation between parts of speech, e.g. pre_ent has relation "prep"
	private String actionRelation;
	/*New combined pos, *non-action* relation, e.g. when a new child needs
	 * to be appended to a StructA, the relationAction is "newchild", and
	 * the pos of the new StructA is combinedPos */
	private String combinedPos;
	private double probability;
	
	public Rule(String rel){
		this.actionRelation = rel;
		this.probability = 1;
	}

	public Rule(String rel, double prob){
		this.actionRelation = rel;
		this.probability = prob;
	}

	public Rule(String rel, String combinedPos_, double prob){
		this.actionRelation = rel;
		this.combinedPos = combinedPos_;
		this.probability = prob;
	}
	
	public String actionRelation(){
		return this.actionRelation;
	}
	
	public String combinedPos(){
		return this.combinedPos;
	}
	
	public double prob(){
		return this.probability;
	}

	@Override
	public String toString() {
		return "Rule [relation=" + actionRelation + ", probability=" + probability + "]";
	}

	// set relation
	public void set_relation(String rel){
		this.actionRelation = rel;
	}

	// set probability
	public void set_prob(double prob){
		this.probability = prob;
	}
	
}
