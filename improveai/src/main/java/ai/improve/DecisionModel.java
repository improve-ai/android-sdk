package ai.improve;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ai.improve.encoder.FeatureEncoder;
import ai.improve.xgbpredictor.ImprovePredictor;
import biz.k11i.xgboost.util.FVec;

public class DecisionModel {
    public static final String Tag = "DecisionModel";

    private final Object lock = new Object();

    private String modelName;

    private DecisionTracker tracker;

    private ImprovePredictor predictor;

    private FeatureEncoder featureEncoder;

    private List<GivensProvider> givensProviders = new ArrayList<>();

    private static AtomicInteger seq = new AtomicInteger(0);

    /**
     * WeakReference is used here to avoid Android activity leaks.
     * A sample activity "LeakTestActivity" is included in the sample project.
     * If WeakReference is removed, leaks can be observed when jumping between
     * "MainActivity" and "LeakTestActivity" many times while network speed is slow.
     */
    private Map<Integer, WeakReference<IMPDecisionModelLoadListener>> listeners = new HashMap<>();

    public static DecisionModel load(URL url) throws Exception {
        final Exception[] loadException = {null};
        DecisionModel decisionModel = new DecisionModel("");
        decisionModel.loadAsync(url, new IMPDecisionModelLoadListener(){
            @Override
            public void onFinish(DecisionModel dm, Exception e) {
                loadException[0] = e;
                synchronized (decisionModel.lock) {
                    decisionModel.lock.notifyAll();
                }
            }
        });
        synchronized (decisionModel.lock) {
            try {
                decisionModel.lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                IMPLog.e(Tag, e.getLocalizedMessage());
            }
        }

        if(loadException[0] != null) {
            IMPLog.e(Tag, "model loading failed, " + url.toString());
            throw loadException[0];
        }

        IMPLog.d(Tag, "load, finish loading model, " + url.toString());

        return decisionModel;
    }

    public void loadAsync(URL url, IMPDecisionModelLoadListener listener) {
        int seq = getSeq();
        listeners.put(seq, new WeakReference<>(listener));
        ModelDownloader.download(url, new ModelDownloader.ModelDownloadListener() {
            @Override
            public void onFinish(ImprovePredictor predictor, Exception e) {
                IMPDecisionModelLoadListener l = listeners.remove(seq).get();
                if(l != null) {
                    if(e != null) {
                        l.onFinish(null, e);
                        IMPLog.d(Tag, "loadAsync, err=" + e.getMessage());
                        return ;
                    }
                    IMPLog.d(Tag, "loadAsync, onFinish OK");

                    DecisionModel.this.setModel(predictor);

                    l.onFinish(DecisionModel.this, null);
                } else {
                    IMPLog.d(Tag, "onFinish, but listener is null");
                }
            }
        });
    }

    public DecisionModel(String modelName) {
        this.modelName = modelName;
    }

    public synchronized void setModel(ImprovePredictor predictor) {
        if(predictor == null) {
            IMPLog.e(Tag, "predictor is null");
            return ;
        }

        this.predictor = predictor;

        if((modelName != null && !modelName.isEmpty()) && !modelName.equals(predictor.getModelMetadata().getModelName())) {
            IMPLog.w(Tag, "Model names don't match: Current model name [" + modelName
                    + "], new model Name [" + predictor.getModelMetadata().getModelName() +"] will be used.");
        }
        this.modelName = predictor.getModelMetadata().getModelName();

        featureEncoder = new FeatureEncoder(predictor.getModelMetadata().getModelSeed(),
                predictor.getModelMetadata().getModelFeatureNames());
    }

    public ImprovePredictor getModel() {
        return predictor;
    }

    public String getModelName() {
        return modelName;
    }

    public DecisionTracker getTracker() {
        return tracker;
    }

    public void setTracker(DecisionTracker tracker) {
        this.tracker = tracker;
    }

    public DecisionModel track(DecisionTracker tracker) {
        this.tracker = tracker;
        return this;
    }

    /**
     * @return an IMPDecision object
     * */
    public <T> Decision chooseFrom(List<T> variants) {
        return new Decision(this).chooseFrom(variants);
    }

    public DecisionModel addGivensProvider(GivensProvider provider) {
        givensProviders.add(provider);
        return this;
    }

    protected Map<String, Object> collectAllGivens() {
        Map<String, Object> allGivens = new HashMap<>();
        for (GivensProvider provider: givensProviders) {
            Map<String, ?> givens = provider.getGivens();
            if(givens != null) {
                allGivens.putAll(givens);
            }
        }
        return allGivens;
    }

    /**
     * @return an IMPDecision object
     * */
    public Decision given(Map<String, Object> givens) {
        return new Decision(this).given(givens);
    }

    /**
     * Returns a list of double scores. If variants is null or empty, an empty
     * list is returned.
     *
     * If this method is called before the model is loaded, or errors occurred
     * while loading the model file, a randomly generated list of descending
     * Gaussian scores is returned.
     *
     * @return a list of double scores.
     *
     * */
    public <T> List<Double> score(List<T> variants, Map<String, ?> givens) {
        List<Double> result = new ArrayList<>();

        if(variants == null || variants.size() <= 0) {
            return result;
        }

        if(predictor == null) {
            IMPLog.e(Tag, "model is not loaded, a randomly generated list of Gaussian numbers is returned");
            return ModelUtils.generateDescendingGaussians(variants.size());
        }

        List<FVec> encodedFeatures = featureEncoder.encodeVariants(variants, givens);
        for (FVec fvec : encodedFeatures) {
            result.add((double)predictor.predictSingle(fvec));
        }

        return result;
    }

    /**
     * If variants.size() != scores.size(), an IndexOutOfBoundException exception will be thrown
     * @return a list of the variants ranked from best to worst by scores
     * */
    public static <T> List<Object> rank(List<T> variants, List<Double> scores) {
        // check the size of variants and scores, and use the bigger one so that
        // an IndexOutOfBoundOfException would be thrown later
        int size = variants.size();
        if(scores.size() > variants.size()) {
            size = scores.size();
        }

        Integer[] indices = new Integer[variants.size()];
        for(int i = 0; i < size; ++i) {
            indices[i] = i;
        }

        Arrays.sort(indices, new Comparator<Integer>() {
            public int compare(Integer obj1, Integer obj2) {
                return scores.get(obj1) < scores.get(obj2) ? 1 : -1;
            }
        });

        List<Object> result = new ArrayList<>(variants.size());
        for(int i = 0; i < indices.length; ++i) {
            result.add(variants.get(indices[i]));
        }

        return result;
    }

    public interface IMPDecisionModelLoadListener {
        /**
         * @param decisionModel null when error occurred while loading the model
         * */
        void onFinish(DecisionModel decisionModel, Exception e);
    }

    private int getSeq() {
        return seq.getAndIncrement();
    }
}
