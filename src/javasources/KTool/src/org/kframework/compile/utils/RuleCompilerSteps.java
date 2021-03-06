package org.kframework.compile.utils;

import org.kframework.compile.transformers.*;
import org.kframework.kil.Definition;
import org.kframework.kil.Rule;
import org.kframework.kil.Variable;
import org.kframework.kil.loader.Context;
import org.kframework.parser.concrete.disambiguate.CollectVariablesVisitor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RuleCompilerSteps extends CompilerSteps<Rule> {

	private Set<Variable> vars;

	public Set<Variable> getVars() {
		return vars;
	}

	public RuleCompilerSteps(Definition def, Context context) {
		super(context);
		this.add(new AddKCell(context));
		this.add(new AddTopCellRules(context));
		this.add(new ResolveAnonymousVariables(context));
		this.add(new ResolveSyntaxPredicates(context));
		this.add(new ResolveListOfK(context));
		this.add(new FlattenSyntax(context));
		ConfigurationStructureVisitor cfgStrVisitor = new ConfigurationStructureVisitor(context);
		def.accept(cfgStrVisitor);
		context.setMaxConfigurationLevel(cfgStrVisitor.getMaxLevel());
		final ResolveContextAbstraction resolveContextAbstraction =
				new ResolveContextAbstraction(context);
		this.add(resolveContextAbstraction);
		this.add(new ResolveOpenCells(context));
	}

	@Override
	public Rule compile(Rule def, String stepName) throws CompilerStepDone {
		CollectVariablesVisitor collectVars = new CollectVariablesVisitor(context);
		def.accept(collectVars);
		vars = new HashSet<Variable>();
		for (List<Variable> collectedVars : collectVars.getVars().values()) {
			vars.add(collectedVars.get(0));
		}
		return super.compile(def, stepName);
	}
}
