package org.kframework.compile.transformers;

import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.KApp;
import org.kframework.kil.KLabelInjection;
import org.kframework.kil.KSorts;
import org.kframework.kil.PriorityBlock;
import org.kframework.kil.Production;
import org.kframework.kil.Rewrite;
import org.kframework.kil.Rule;
import org.kframework.kil.Sort;
import org.kframework.kil.Syntax;
import org.kframework.kil.Term;
import org.kframework.kil.TermCons;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.kil.KItemProjection;
import org.kframework.kil.visitors.exceptions.TransformerException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author AndreiS
 */
public class AddInjections extends CopyOnWriteTransformer{

//    private Stack<String> expectedSortStack;

    public AddInjections(Context context) {
        super("", context);
//        expectedSortStack = new Stack<>();
    }

    @Override
    public Syntax transform(Syntax node) throws TransformerException {
        assert node.getPriorityBlocks().size() == 1;
        assert node.getPriorityBlocks().get(0).getProductions().size() == 1;

        String sort = node.getSort().getName();
        Production production = node.getPriorityBlocks().get(0).getProductions().get(0);
        production = (Production) transform(production);

        if ((sort.equals(KSorts.KLABEL) && production.containsAttribute(Attribute.FUNCTION_KEY))
                || sort.equals(KSorts.K) || sort.equals(KSorts.KLIST)) {
            production = production.shallowCopy();
            production.setSort(KSorts.KITEM);
        }

        Syntax returnNode;
        if (production != node.getPriorityBlocks().get(0).getProductions().get(0)) {
            String cons = context.conses.inverse().get(
                    node.getPriorityBlocks().get(0).getProductions().get(0));
            context.conses.forcePut(cons, production);

            returnNode = node.shallowCopy();
            PriorityBlock priorityBlock = node.getPriorityBlocks().get(0).shallowCopy();
            returnNode.setPriorityBlocks(Collections.singletonList(priorityBlock));
            priorityBlock.setProductions(Collections.singletonList(production));

            if (!production.getSort().equals(sort)) {
                returnNode.setSort(returnNode.getSort().shallowCopy());
                returnNode.getSort().setName(production.getSort());
            }
        } else {
            returnNode = node;
        }

        return returnNode;
    }

    /** Transforms {@code Sort} instances occurring as part of
     * {@link org.kframework.kil.ProductionItem}. Other instances are not changed. */
    @Override
    public Sort transform(Sort node) {
        if (node.getName().equals(KSorts.KLABEL) || node.getName().equals(KSorts.KLIST)) {
            Sort returnNode = node.shallowCopy();
            returnNode.setName(KSorts.KITEM);
            return returnNode;
        } else {
            return node;
        }
    }



//    @Override
//    public Context transform(Context node) throws TransformerException {
//        assert expectedSortStack.isEmpty();
//
//        expectedSortStack.push(KSorts.K);
//        Context returnNode = (Context) super.transform(node);
//        expectedSortStack.pop();
//
//        return returnNode;
//    }

//    @Override
//    public Rule transform(Rule node) throws TransformerException {
//        assert expectedSortStack.isEmpty();
//
//        if (node.containsAttribute(Attribute.FUNCTION_KEY)
//                || node.containsAttribute(Attribute.PREDICATE_KEY)) {
//            expectedSortStack.push(KSorts.KITEM);
//        } else {
//            expectedSortStack.push(KSorts.K);
//        }
//        Rule returnNode = (Rule) super.transform(node);
//        expectedSortStack.pop();
//
//        return returnNode;
//    }
//
//
//    @Override
//    public Cell transform(Cell node) throws TransformerException {
//        expectedSortStack.push(KSorts.K);
//        Cell returnNode = (Cell) super.transform(node);
//        expectedSortStack.pop();
//
//        return returnNode;
//    }
//
//    @Override
//    public ASTNode transform(KApp node)  throws TransformerException {
//        expectedSortStack.push(KSorts.KLABEL);
//        Term transformedKLabel = (KLabel) node.getLabel().accept(this);
//        assert transformedKLabel != null && transformedKLabel.getSort().equals(KSorts.KLABEL);
//        expectedSortStack.pop();
//
//        expectedSortStack.push(KSorts.KLIST);
//        Term transformedKList = (KList) node.getChild().accept(this);
//        assert transformedKList != null && transformedKList.getSort().equals(KSorts.KLIST);
//        expectedSortStack.pop();
//
//        KApp returnNode;
//        if (node.getLabel() != transformedKLabel || node.getChild() != transformedKList) {
//            returnNode = node.shallowCopy();
//            returnNode.setLabel(transformedKLabel);
//            returnNode.setChild(transformedKList);
//        } else {
//            returnNode = node;
//        }
//
//        return returnNode;
//    }
//
//    @Override
//    public ASTNode transform(KLabelConstant node) throws TransformerException {
//        if (!expectedSortStack.peek().equals(KSorts.KLABEL)) {
//            return KApp.of(new KLabelInjection(node));
//        } else {
//            return node;
//        }
//
//    }
//
//    @Override
//    public ASTNode transform(KList node) throws TransformerException {
//        List<Term> transformedTerms = transformList(node.getContents(), KSorts.KLIST);
//
//        Term returnNode;
//        if (transformedTerms != node.getContents()) {
//            returnNode = node.shallowCopy();
//            node.setContents(transformedTerms);
//        } else {
//            returnNode = node;
//        }
//
//        if (expectedSortStack.peek().equals(KSorts.KITEM)
//                || expectedSortStack.peek().equals(KSorts.K)) {
//            returnNode = KApp.of(new KLabelInjection(returnNode));
//        }
//
//        return returnNode;
//    }
//
//    @Override
//    public ASTNode transform(KSequence node) throws TransformerException {
//        List<Term> transformedTerms = transformList(node.getContents(), KSorts.K);
//
//        Term returnNode;
//        if (transformedTerms != node.getContents()) {
//            returnNode = node.shallowCopy();
//            node.setContents(transformedTerms);
//        } else {
//            returnNode = node;
//        }
//
//        if (expectedSortStack.peek().equals(KSorts.KITEM)) {
//            returnNode = KApp.of(new KLabelInjection(returnNode));
//        }
//
//        return returnNode;
//    }
//
//    @Override
//    public ASTNode transform(TermCons node) throws TransformerException {
//        List<Term> transformedTerms = transformList(node.getContents(), KSorts.K);
//
//        Term returnNode;
//        if (transformedTerms != node.getContents()) {
//            returnNode = node.shallowCopy();
//            node.setContents(transformedTerms);
//        } else {
//            returnNode = node;
//        }
//
//        if (expectedSortStack.peek().equals(node.getSort())
//                && !expectedSortStack.peek().equals(KSorts.KITEM)) {
//            returnNode = new KItemProjection(expectedSortStack.peek(), returnNode);
//        }
//
//        return returnNode;
//    }
//
//    @Override
//    public ASTNode transform(Variable node) throws TransformerException {
//        Term returnNode;
//        switch (node.getSort()) {
//            case KSorts.KLABEL:
//                break;
//            default:
//                returnNode = node;
//        }
//
//        if (expectedSortStack.peek().equals(KSorts.KLABEL)
//                && !node.getSort().equals(KSorts.KLABEL)) {
//            return KApp.of(new KLabelInjection(node));
//        } else if (expectedSortStack.peek().equals(KSorts.KITEM) && )
//        return expectedSortStack.peek().equals(KSorts.K) && ? node : KApp.of(new KLabelInjection
//                (node));
//
//    }
//
//    private List<Term> transformList(List<Term> terms, String expectedSort)
//            throws TransformerException {
//        boolean change = false;
//        List<Term> transformedTerms = new ArrayList<>();
//        for (Term term : terms) {
//            expectedSortStack.push(expectedSort);
//            Term transformedTerm = (Term) term.accept(this);
//            assert transformedTerm != null;
//            expectedSortStack.pop();
//
//            transformedTerms.add(transformedTerm);
//            if (transformedTerm != term) {
//                change = true;
//            }
//        }
//
//        return change ? transformedTerms : terms;
//    }

    @Override
    public Rule transform(Rule node) throws TransformerException {
        Rule transformedNode = (Rule) super.transform(node);
        if (!node.containsAttribute(Attribute.FUNCTION_KEY)) {
            return transformedNode;
        }

        Term left = ((Rewrite) transformedNode.getBody()).getLeft();
        Term right = ((Rewrite) transformedNode.getBody()).getRight();
        if (!(left instanceof KItemProjection)) {
            return transformedNode;
        }

        transformedNode = transformedNode.shallowCopy();
        Rewrite transformedBody = (Rewrite) transformedNode.getBody().shallowCopy();
        transformedNode.setBody(transformedBody);
        transformedBody.setLeft(((KItemProjection) left).getTerm(), context);
        transformedBody.setRight(KApp.of(new KLabelInjection(right)), context);
        return transformedNode;
    }

    @Override
    public ASTNode transform(TermCons node) throws TransformerException {
        boolean change = false;
        List<Term> transformedContents = new ArrayList<>();
        for (Term term : node.getContents()) {
            Term transformedTerm = (Term) term.accept(this);
            assert transformedTerm != null;

            if (transformedTerm.getSort().equals(KSorts.KLABEL)
                    || transformedTerm.getSort().equals(KSorts.KLIST)) {
                transformedTerm = KApp.of(new KLabelInjection(node));
            }
            transformedContents.add(transformedTerm);

            if (transformedTerm != term) {
                change = true;
            }
        }

        TermCons transformedNode;
        if (change) {
            transformedNode = node.shallowCopy();
            transformedNode.setContents(transformedContents);
        } else {
            transformedNode = node;
        }

        if (node.getSort().equals(KSorts.K) || node.getSort().equals(KSorts.KLABEL)
                || node.getSort().equals(KSorts.KLIST)) {
            return new KItemProjection(node.getSort(), transformedNode);
        } else {
            return transformedNode;
        }
    }

}
