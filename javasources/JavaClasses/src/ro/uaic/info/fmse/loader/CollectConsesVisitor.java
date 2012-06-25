package ro.uaic.info.fmse.loader;

import ro.uaic.info.fmse.k.Production;
import ro.uaic.info.fmse.visitors.BasicVisitor;

public class CollectConsesVisitor extends BasicVisitor {
	@Override
	public void visit(Production node) {
		if (node.getAttributes().containsKey(Constants.CONS_cons_ATTR))
			DefinitionHelper.conses.put(node.getAttributes().get(Constants.CONS_cons_ATTR), node);
	}
}