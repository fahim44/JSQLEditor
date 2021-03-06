package com.it.soul.lab.sql.query.models;

public class OrExpression extends AndExpression {

	public OrExpression(ExpressionInterpreter lhr, ExpressionInterpreter rhr) {
		super(lhr, rhr);
	}

	@Override
	public String interpret() {
		if (skipParenthesis() == false) return leftParenthesis + " " + lhr.interpret() + " OR " + rhr.interpret() + " " + rightParenthesis;
		else return lhr.interpret() + " OR " + rhr.interpret();
	}

}
