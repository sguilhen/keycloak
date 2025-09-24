package org.keycloak.models.workflow.conditions.expression;

import org.antlr.v4.runtime.tree.ParseTree;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.workflow.WorkflowConditionProvider;
import org.keycloak.models.workflow.WorkflowConditionProviderFactory;
import org.keycloak.models.workflow.WorkflowEvent;

import java.util.List;
import java.util.stream.Collectors;

public class BooleanConditionEvaluator extends BooleanConditionBaseVisitor<Boolean> {

    private final KeycloakSession session;
    private final WorkflowEvent event;

    public BooleanConditionEvaluator(KeycloakSession session, WorkflowEvent event) {
        this.session = session;
        this.event = event;
    }

    @Override
    public Boolean visitEvaluator(BooleanConditionParser.EvaluatorContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Boolean visitExpression(BooleanConditionParser.ExpressionContext ctx) {
        if (ctx.expression() != null && ctx.OR() != null) {
            return visit(ctx.expression()) || visit(ctx.andExpression());
        }
        return visit(ctx.andExpression());
    }

    @Override
    public Boolean visitAndExpression(BooleanConditionParser.AndExpressionContext ctx) {
        if (ctx.andExpression() != null && ctx.AND() != null) {
            return visit(ctx.andExpression()) && visit(ctx.notExpression());
        }
        return visit(ctx.notExpression());
    }

    @Override
    public Boolean visitNotExpression(BooleanConditionParser.NotExpressionContext ctx) {
        if (ctx.NOT() != null) {
            return !visit(ctx.notExpression());
        }
        return visit(ctx.atom());
    }

    @Override
    public Boolean visitAtom(BooleanConditionParser.AtomContext ctx) {
        if (ctx.conditionCall() != null) {
            return visit(ctx.conditionCall());
        }
        return visit(ctx.expression());
    }

    @Override
    public Boolean visitConditionCall(BooleanConditionParser.ConditionCallContext ctx) {
        String conditionName = ctx.Identifier().getText();

        KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();
        WorkflowConditionProviderFactory<WorkflowConditionProvider> providerFactory =
                (WorkflowConditionProviderFactory<WorkflowConditionProvider>) sessionFactory.getProviderFactory(WorkflowConditionProvider.class, conditionName);
        if (providerFactory == null) {
            throw new IllegalArgumentException("Could not find condition provider: " + conditionName);
        }

        WorkflowConditionProvider conditionProvider = providerFactory.create(session, extractParameterList(ctx.parameterList()));
        return conditionProvider.evaluate(event);
    }

    private List<String> extractParameterList(BooleanConditionParser.ParameterListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        return ctx.StringLiteral().stream()
                .map(this::visitStringLiteral)
                .collect(Collectors.toList());
    }

    private String visitStringLiteral(ParseTree ctx) {
        String text = ctx.getText();
        return text.substring(1, text.length() - 1).replace("\"\"", "\"");
    }
}
