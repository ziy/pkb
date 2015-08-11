package wrappers;


import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import cc.mallet.fst.HMM;
import cc.mallet.fst.HMMTrainerByLikelihood;
import cc.mallet.fst.SegmentationEvaluator;
import cc.mallet.fst.TransducerEvaluator;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.SimpleTaggerSentence2TokenSequence;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;

public class HmmTagger {

  public static List<String> run(String trainingFilename, String testingFilename)
          throws FileNotFoundException, IOException {
    ArrayList<Pipe> pipes = new ArrayList<Pipe>();

    pipes.add(new SimpleTaggerSentence2TokenSequence());
    pipes.add(new TokenSequence2FeatureSequence());

    Pipe pipe = new SerialPipes(pipes);

    InstanceList trainingInstances = new InstanceList(pipe);
    InstanceList testingInstances = new InstanceList(pipe);

    trainingInstances.addThruPipe(new LineGroupIterator(
            new BufferedReader(new InputStreamReader(new FileInputStream(trainingFilename))),
            Pattern.compile("^\\s*$"), true));
    testingInstances.addThruPipe(new LineGroupIterator(
            new BufferedReader(new InputStreamReader(new FileInputStream(testingFilename))),
            Pattern.compile("^\\s*$"), true));

    HMM hmm = new HMM(pipe, null);
    hmm.addStatesForLabelsConnectedAsIn(trainingInstances);

    HMMTrainerByLikelihood trainer = new HMMTrainerByLikelihood(hmm);
    TransducerEvaluator testingEvaluator = new SegmentationEvaluator(testingInstances, "testing");
    trainer.train(trainingInstances, 100);
    testingEvaluator.evaluate(trainer);

    return testingInstances.stream().map(Instance::getData).map(Sequence.class::cast)
            .map(hmm::transduce)
            .flatMap(output -> IntStream.range(0, output.size()).mapToObj(output::get))
            .map(String.class::cast).collect(toList());

    // hmm.print();
  }

}
