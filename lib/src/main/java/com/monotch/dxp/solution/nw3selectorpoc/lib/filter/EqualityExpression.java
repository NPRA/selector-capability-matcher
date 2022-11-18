package com.monotch.dxp.solution.nw3selectorpoc.lib.filter;

import com.monotch.dxp.solution.nw3selectorpoc.lib.Trilean;
import org.apache.qpid.server.filter.ConstantExpression;
import org.apache.qpid.server.filter.Expression;
import org.apache.qpid.server.filter.SelectorParsingException;

import java.util.Collection;

public class EqualityExpression<E> extends ComparisonExpression<E> {
    public EqualityExpression(Expression<E> left, Expression<E> right) {
        super(left, right);
    }

    public static <E> TrileanExpression<E> createEqual(Expression<E> left, Expression<E> right) {
        checkEqualOperand(left);
        checkEqualOperand(right);
        checkEqualOperandCompatability(left, right);
        return new EqualityExpression<>(left, right);
    }

    private static <E> void checkEqualOperandCompatability(Expression<E> left, Expression<E> right) {
        if ((left instanceof ConstantExpression) && (right instanceof ConstantExpression)) {
            if ((left instanceof TrileanExpression) && !(right instanceof TrileanExpression)) {
                throw new SelectorParsingException("'" + left + "' cannot be compared with '" + right + "'");
            }
        }
    }

    @Override
    public Object evaluate(E message) {
        Object lv = getLeft().evaluate(message);
        Object rv = getRight().evaluate(message);
        if ((lv == null) ^ (rv == null)) {
            return Trilean.UNKNOWN;
        }
        if ((lv == rv) || lv.equals(rv)) {
            return Trilean.TRUE;
        }
        if (lv instanceof Collection && ((Collection<?>) lv).contains(rv)) {
            return Trilean.UNKNOWN;
        }
        if (rv instanceof Collection && ((Collection<?>) rv).contains(lv)) {
            return Trilean.UNKNOWN;
        }
        if ((lv instanceof Comparable) && (rv instanceof Comparable)) {
            return compare((Comparable) lv, (Comparable) rv);
        }
        return Trilean.FALSE;
    }

    @Override
    protected boolean convertComparatorValueToBoolean(int answer) {
        return answer == 0;
    }

    @Override
    public String getExpressionSymbol() {
        return "=";
    }
}
