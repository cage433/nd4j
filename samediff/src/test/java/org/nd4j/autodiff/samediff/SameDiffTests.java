package org.nd4j.autodiff.samediff;

import org.junit.Test;
import org.nd4j.autodiff.opstate.OpExecAction;
import org.nd4j.autodiff.opstate.OpState;
import org.nd4j.autodiff.samediff.impl.SDVariable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.api.ops.impl.transforms.Sigmoid;
import org.nd4j.linalg.api.ops.impl.transforms.SigmoidDerivative;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.util.ArrayUtil;

import java.io.File;
import java.util.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by agibsonccc on 4/11/17.
 */
public class SameDiffTests {
    @Test
    public void testSigmoid() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1,4,4);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable sigmoid = sameDiff.sigmoid(x);
        assertEquals("sigmoid(x)",sigmoid.getVarName());
        assertEquals(2, sameDiff.graph().numVertices());
        assertEquals(1, sameDiff.graph().getEdges().size());
        assertArrayEquals(arr.shape(), sigmoid.getShape());
        assertEquals(1, sameDiff.graph().getVertexInDegree(sigmoid.getDifferentialFunction().getVertexId()));
        int[] sorted = new int[] { x.getArrayField().getVertexId(), sigmoid.getDifferentialFunction().getVertexId() };
        assertArrayEquals(sorted, sameDiff.graph().topologicalSort());
        assertEquals(1, sameDiff.graph().getOpOrder().getActions().size());
        OpState opState = sameDiff.graph().getOpOrder().getActions().get(0).getOpState();
        assertEquals("sigmoid",opState.getOpName());
        sameDiff.allocate();
        Op op = sameDiff.createOp(OpState.OpType.TRANSFORM, sameDiff.graph().getOpOrder().getActions().get(0));
        assertTrue(op instanceof Sigmoid);
        Nd4j.getExecutioner().exec(op);
        assertEquals(Transforms.sigmoid(Nd4j.linspace(1,4,4)),op.z());
    }

    @Test
    public void testSum() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1, 4, 4));
        SDVariable x = sameDiff.var("x", arr);
        SDVariable result = sameDiff.sum(x, 1);
        assertEquals("sum(x)", result.getVarName());
        assertEquals(2, sameDiff.graph().numVertices());
        assertEquals(1, sameDiff.graph().getEdges().size());
        assertArrayEquals(arr.shape(),result.getShape());
        assertArrayEquals(new int[] { 1, 2 }, sameDiff.graph().topologicalSort());
    }

    @Test
    public void testReshape() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable result = sameDiff.reshape(x, 2, 2);
        assertEquals("reshape(x)",result.getVarName());
        assertEquals(2, sameDiff.graph().numVertices());
        assertEquals(1, sameDiff.graph().getEdges().size());
        assertArrayEquals(new int[]{2,2},result.getShape());

    }

    @Test
    public void testTranspose() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4));
        SDVariable x = sameDiff.var("x",arr);
        SDVariable result = sameDiff.transpose(x);
        assertEquals("transpose(x)",result.getVarName());
        assertEquals(2, sameDiff.graph().numVertices());
        assertEquals(1, sameDiff.graph().getEdges().size());
        assertArrayEquals(new int[]{4,1},result.getShape());

    }

    @Test
    public void testDistance() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.cosineSimilarity(x,y,1);
        SDVariable addResult = result.add(result);

        assertEquals("cosineSimilarity(x,y)",result.getVarName());
        assertEquals(3, sameDiff.graph().numVertices());
        assertEquals(2, sameDiff.graph().getEdges().get(0).size());
        assertArrayEquals(new int[]{1,2},result.getShape());
    }

    @Test
    public void testTensorGradMmul() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.mmul(0,x,y);
        SDVariable otherResult = result.add(result);
        assertEquals("mmul(x,y)",result.getVarName());
        assertEquals(5, sameDiff.graph().numVertices()); // XXX: Why 5 instead of 3?
        assertEquals(3, sameDiff.graph().getEdges().size()); // XXX: Why 3 instead of 2?
        assertArrayEquals(new int[]{2,2},result.getShape());
    }


    @Test
    public void testGetInputs() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.mmul(0,x,y);
        SDVariable otherResult = result.add(result);
        assertEquals(2, sameDiff.graph().getInputs().size());
    }

    @Test
    public void testGetOutputs() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.mmul(0,x,y);
        SDVariable otherResult = result.add(result);
        assertEquals(2, sameDiff.graph().getOutputs().size());
    }

    @Test
    public void testEval() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1,4,4);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable sigmoid = sameDiff.sigmoid(x);
        INDArray assertion = Transforms.sigmoid(arr);
        INDArray[] eval = sameDiff.eval(Collections.singletonMap("x",arr));
        assertEquals(assertion,eval[0]);

    }

    @Test
    public void testEvalAddSelf() {
        /**
         * Note this test fails yet due to needing
         * to validate simple cases like x * x
         * matching number of inputs.
         */
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1,4,4);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable sigmoid = x.mul(x);
        INDArray assertion = arr.mul(arr);
        INDArray[] eval = sameDiff.eval(Collections.singletonMap("x",arr));
        assertEquals(assertion,eval[0]);

    }

    @Test
    public void testEvalAdd() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1,4,4);
        INDArray yArr = arr.dup();
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",yArr);

        SDVariable sigmoid = x.mul(y);
        INDArray assertion = arr.mul(arr);
        Map<String,INDArray> vars = new HashMap<>();
        vars.put("x",arr);
        vars.put("y",yArr);
        INDArray[] eval = sameDiff.eval(vars);
        assertEquals(assertion,eval[0]);

    }




    @Test
    public void testTensorGradTensorMmul() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,8,8)).reshape(2,2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.tensorMmul(x,y,new int[][]{{0},{1}},0);
        assertEquals("tensorMmul(x,y)",result.getVarName());
        assertEquals(3, sameDiff.graph().numVertices());
        assertEquals(2, sameDiff.graph().getEdges().size());
        assertArrayEquals(ArrayUtil.getTensorMmulShape(new int[]{2,2,2},new int[]{2,2,2},new int[][]{{0},{1}}),result.getShape());
        assertEquals(32, sameDiff.numElements());
    }

    @Test
    public void testDup() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,8,8)).reshape(2,2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SameDiff tg2 = sameDiff.dup();
        assertEquals(sameDiff, tg2);
    }

    @Test
    public void testOpExecutionWithAutoDiff() {
        SameDiff sameDiff = SameDiff.create();

        INDArray arr = Nd4j.linspace(1,4,4);

        SDVariable x = sameDiff.var("x", arr);
        SDVariable sigmoid = sameDiff.sigmoid(x);
        SDVariable grad = sameDiff.grad(sigmoid, x);

        List<OpExecAction> actions = sameDiff.graph().getOpOrder().getActions();

        OpState opState = actions.get(0).getOpState();
        assertEquals("sigmoid", opState.getOpName());

        OpState opState2 = actions.get(1).getOpState();
        assertEquals("sigmoidderivative", opState2.getOpName());

        sameDiff.allocate();

        Op op1 = sameDiff.createOp(actions.get(0).getOpState().getOpType(), actions.get(0));
        assertTrue(op1 instanceof Sigmoid);
        Nd4j.getExecutioner().exec(op1);
        assertEquals(Transforms.sigmoid(arr), op1.z());

        Op op2 = sameDiff.createOp(actions.get(1).getOpState().getOpType(), actions.get(1));
        assertTrue(op2 instanceof SigmoidDerivative);
        Nd4j.getExecutioner().exec(op2);
    }


    @Test
    public void testLogisticRegression() throws Exception {
        SameDiff sameDiff = SameDiff.create();
        INDArray inputs = Nd4j.create(new double[][]{
                {0.52, 1.12,  0.77},
                {0.88, -1.08, 0.15},
                {0.52, 0.06, -1.30},
                {0.74, -2.49, 1.39}
        });

        INDArray labels = Nd4j.create(new double[]{1,1,0,0}).reshape(4,1);

        INDArray weights = Nd4j.rand(3,1,1);

        SDVariable x = sameDiff.var("x",inputs);
        SDVariable y = sameDiff.var("y",labels);
        SDVariable w = sameDiff.var("w",weights);

        SDVariable learningRate = sameDiff.scalar("lr",0.01);

        SDVariable preOutput = sameDiff.mmul(0,x,w);

        SDVariable outputs = sameDiff.sigmoid(preOutput);
        assertEquals(6,sameDiff.graph().numVertices());
        assertEquals(3,sameDiff.graph().getEdges().size());
        //    label_probabilities = preds * targets + (1 - preds) * (1 - targets)
        SDVariable outputTimesY = outputs.mul(y);
        SDVariable oneMinusOutput = outputs.rsub(sameDiff.scalar("one",1.0));
        SDVariable probs = outputTimesY.add(oneMinusOutput.mul(y.rsub(sameDiff.scalar("onetwo",1.0))));
        SDVariable logProbs = sameDiff.log(probs);
        SDVariable sum = sameDiff.sum(logProbs,Integer.MAX_VALUE);
        SDVariable negSum = sameDiff.neg(sum);
        SDVariable outputGrad = sameDiff.grad(negSum,w);
        assertArrayEquals(new int[]{3,1},outputGrad.getShape());
        SDVariable preUpdate = w.mul(outputGrad);
        SDVariable update = preUpdate.mul(learningRate);
        w = w.sub(update);

        System.out.println(sameDiff.graph().numVertices() + " and " + sameDiff.graph().getEdges().size());
        List<Op> ops = sameDiff.exec();
        for(int i = 0; i < 5; i++) {
            INDArray output =  sameDiff.execAndEndResult(ops);
            System.out.println("Update " + output);
        }

        System.out.println(ops);
    }

}