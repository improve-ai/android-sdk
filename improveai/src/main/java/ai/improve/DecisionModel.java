package ai.improve;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import ai.improve.downloader.ModelDownloader;
import ai.improve.encoder.FeatureEncoder;
import ai.improve.log.IMPLog;
import ai.improve.provider.GivensProvider;
import ai.improve.util.ModelMap;
import ai.improve.util.ModelUtils;
import ai.improve.util.Utils;
import ai.improve.xgbpredictor.ImprovePredictor;
import biz.k11i.xgboost.util.FVec;

public class DecisionModel {
    /** @hidden */
    public static final String Tag = "DecisionModel";

    private static String sDefaultTrackURL = null;

    private static String sDefaultTrackApiKey = null;

    private final Object lock = new Object();

    private final String modelName;

    private String trackURL;

    private String trackApiKey;

    private DecisionTracker tracker;

    private ImprovePredictor predictor;

    private FeatureEncoder featureEncoder;

    private static final AtomicInteger seq = new AtomicInteger(0);

    /** @hidden */
    protected boolean enableTieBreaker = true;

    private GivensProvider givensProvider;

    private static GivensProvider defaultGivensProvider;

    private final static ModelMap instances = new ModelMap();

    private final Map<Integer, LoadListener> listeners = new ConcurrentHashMap<>();

    /**
     * It's an equivalent of DecisionModel(modelName, defaultTrackURL, defaultTrackApiKey)
     * We suggest to have the defaultTrackURL/defaultTrackApiKey set on startup before creating
     * any DecisionModel instances.
     * @param modelName Length of modelName must be in range [1, 64]; Only alphanumeric
     *                  characters([a-zA-Z0-9]), '-', '.' and '_' are allowed in the modelName
     *                  and the first character must be an alphanumeric one.
     * */
    public DecisionModel(String modelName) {
        this(modelName, sDefaultTrackURL, sDefaultTrackApiKey);
    }

    /**
     * @param modelName Length of modelName must be in range [1, 64]; Only alphanumeric
     *                  characters([a-zA-Z0-9]), '-', '.' and '_' are allowed in the modelName
     *                  and the first character must be an alphanumeric one.
     * @param trackURL url for tracking decisions. If trackURL is null, no decisions would be
     *                 tracked. If trackURL is not a valid URL, an exception would be thrown.
     * @param trackApiKey will be attached to the header fields of all the post request for tracking
     * @throws IllegalArgumentException Thrown if an invalid modelName or an invalid trackURL
     */
    public DecisionModel(String modelName, String trackURL, String trackApiKey) {
        if(!isValidModelName(modelName)) {
            throw new IllegalArgumentException("invalid modelName: [" + modelName + "]");
        }
        this.modelName = modelName;
        this.trackApiKey = trackApiKey;

        setTrackURL(trackURL);

        this.givensProvider = defaultGivensProvider;
    }

    /**
     * Get a shared model instance. If a DecisionModel instance with the given modelName has not
     * been created yet, get(modelName) would create one and cache it, and subsequent calls would
     * simply return the cached DecisionModel instance.
     * @param modelName name of the model
     * @return A DecisionModel instance.
     */
    public static DecisionModel get(String modelName) {
        return instances.get(modelName);
    }

    /**
     * Override the cached DecisionModel instance.
     * @param modelName name of the model.
     * @param decisionModel the new DecisionModel instance. When null, the cached DecisionModel instance
     *                      would be removed.
     */
    public static void put(String modelName, DecisionModel decisionModel) {
        instances.put(modelName, decisionModel);
    }

    /**
     * Load a model synchronously. Calling this method would block the current thread, so please
     * try not do it in the UI thread.
     * @param modelUrl A url that can be a local file path, a remote http url that points to a
     *                 model file, or a bundled asset. Urls that ends with '.gz' are considered gzip
     *                 compressed, and will be handled appropriately. Bundled model asset urls
     *                 appears a bit different. Suppose that you have a bundled model file in folder
     *                 "assets/models/my_model.xgb.gz", then modelUrl should be
     *                 new URL("file:///android_asset/models/my_model.xgb").
     * @return Returns self.
     * @throws IOException Thrown if the model failed to load.
     */
    public DecisionModel load(URL modelUrl) throws IOException {
        final IOException[] downloadException = {null};
        LoadListener listener = new LoadListener() {
            @Override
            public void onLoad(DecisionModel decisionModel) {
                synchronized (decisionModel.lock) {
                    decisionModel.lock.notifyAll();
                }
            }

            @Override
            public void onError(IOException e) {
                synchronized (DecisionModel.this.lock) {
                    downloadException[0] = e;
                    DecisionModel.this.lock.notifyAll();
                }
            }
        };

        loadAsync(modelUrl, listener);
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                IMPLog.e(Tag, e.getLocalizedMessage());
            }
        }

        if(downloadException[0] != null) {
            throw downloadException[0];
        }

        return this;
    }

    /**
     * @param modelUrl A url that can be a local file path, a remote http url that points to a
     *                 model file, or a bundled asset. Urls that ends with '.gz' are considered gzip
     *                 compressed, and will be handled appropriately. Bundled model asset urls
     *                 appears a bit different. Suppose that you have a bundled model file in folder
     *                 "assets/models/my_model.xgb.gz", then modelUrl should be
     *                 new URL("file:///android_asset/models/my_model.xgb").
     */
    public void loadAsync(URL modelUrl) {
        loadAsync(modelUrl, null);
    }

    /**
     * Notice that it's not recommended to call this method directly in an Android Activity as it
     * may cause leaks. Before we add a cancel method to allow aborting downloading tasks, you may
     * have to call loadAsync() in something like a Singleton class.
     * @deprecated  The callback method signature will likely have to change for multiple URLs
     * @param modelUrl A url that can be a local file path, a remote http url that points to a
     *                 model file, or a bundled asset. Urls that ends with '.gz' are considered gzip
     *                 compressed, and will be handled appropriately. Bundled model asset urls
     *                 appears a bit different. Suppose that you have a bundled model file in folder
     *                 "assets/models/my_model.xgb.gz", then modelUrl should be
     *                 new URL("file:///android_asset/models/my_model.xgb").
     * @param listener The callback that will run when the model is loaded.
     * */
    @Deprecated
    public void loadAsync(URL modelUrl, LoadListener listener) {
        int seq = getSeq();
        if(listener != null) {
            listeners.put(seq, listener);
        }
        ModelDownloader.download(modelUrl, new ModelDownloader.ModelDownloadListener() {
            @Override
            public void onFinish(ImprovePredictor predictor, IOException e) {
                LoadListener l = listeners.remove(seq);
                if(l == null) {
                    // Don't return here, just give a warning here.
                    IMPLog.d(Tag, "loadAsync finish loading model, but listener is null, " + modelUrl.toString());
                }

                if(e != null) {
                    if(l != null) {
                        l.onError(e);
                    }
                } else {
                    DecisionModel.this.setModel(predictor);

                    IMPLog.d(Tag, "loadAsync, finish loading model, " + modelUrl.toString());
                    if (l != null) {
                        l.onLoad(DecisionModel.this);
                    }
                }
            }
        });
    }

    public synchronized String getTrackURL() {
        return trackURL;
    }

    /**
     * @param trackURL url for decision tracking. If set as null, no decisions would be tracked.
     * @throws IllegalArgumentException Thrown if trackURL is nonnull and not a valid URL.
     * */
    public synchronized void setTrackURL(String trackURL) {
        this.trackURL = trackURL;
        if(trackURL == null) {
            this.tracker = null;
        } else {
            this.tracker = new DecisionTracker(Utils.toURL(trackURL), this.trackApiKey);
        }
    }

    public static String getDefaultTrackURL() {
        return sDefaultTrackURL;
    }

    /**
     * @param trackURL default trackURL for tracking decisions.
     * @throws IllegalArgumentException if trackURL is nonnull and not a valid url
     */
    public static void setDefaultTrackURL(String trackURL) {
        if(trackURL != null && !Utils.isValidURL(trackURL)) {
            throw new IllegalArgumentException("invalid trackURL: " + trackURL);
        }
        sDefaultTrackURL = trackURL;
    }

    public synchronized String getTrackApiKey() {
        return trackApiKey;
    }

    public synchronized void setTrackApiKey(String trackApiKey) {
        this.trackApiKey = trackApiKey;
        if(tracker != null) {
            tracker.setTrackApiKey(trackApiKey);
        }
    }

    public static String getDefaultTrackApiKey() {
        return sDefaultTrackApiKey;
    }

    public static void setDefaultTrackApiKey(String defaultTrackApiKey) {
        sDefaultTrackApiKey = defaultTrackApiKey;
    }

    public GivensProvider getGivensProvider() {
        return givensProvider;
    }

    public void setGivensProvider(GivensProvider givensProvider) {
        this.givensProvider = givensProvider;
    }

    public static GivensProvider getDefaultGivensProvider() {
        return defaultGivensProvider;
    }

    public static void setDefaultGivensProvider(GivensProvider givensProvider) {
        defaultGivensProvider = givensProvider;
    }

    private synchronized void setModel(ImprovePredictor predictor) {
        if(predictor == null) {
            IMPLog.e(Tag, "predictor is null");
            return ;
        }

        this.predictor = predictor;

        if(!modelName.equals(predictor.getModelMetadata().getModelName())){
            IMPLog.w(Tag, "Model names don't match: current model name [" + modelName
                    + "], model name extracted [" + predictor.getModelMetadata().getModelName() +"], ["
                    + modelName + "] will be used.");
        }

        featureEncoder = new FeatureEncoder(predictor.getModelMetadata().getModelSeed(),
                predictor.getModelMetadata().getModelFeatureNames());
    }

    public String getModelName() {
        return modelName;
    }

    /** @hidden */
    protected DecisionTracker getTracker() {
        return tracker;
    }

    /** @hidden */
    protected FeatureEncoder getFeatureEncoder() {
        return featureEncoder;
    }

    /**
     * Check whether the model is loaded.
     * @return {@code true} if the model is loaded.
     * @hidden
     */
    protected boolean isLoaded() {
        return predictor != null;
    }

    /**
     * @param givens Additional context info that will be used with each of the variants to calculate
     *              its feature vector.
     * @return A DecisionContext object.
     */
    public DecisionContext given(Map<String, ?> givens) {
        return new DecisionContext(this, givens);
    }

    /**
     * If this method is called before the model is loaded, or errors occurred
     * while loading the model file, a randomly generated list of descending
     * Gaussian scores is returned.
     * @param variants Variants can be any JSON encodeable data structure of arbitrary complexity,
     *                 including nested maps, arrays, strings, numbers, nulls, and booleans.
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     * @return scores of the variants
     */
    public List<Double> score(List<?> variants) {
        return given(null).score(variants);
    }

    /**
     * Equivalent to decide(variants, false).
     * @param <T> Could be numbers, strings, booleans, nulls, or nested list/map structure of these
     *           types.
     * @param variants Variants can be any JSON encodeable data structure of arbitrary complexity,
     *                 including nested dictionaries, lists, maps, strings, numbers, nulls, and
     *                 booleans.
     * @return A Decision object
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     */
    public <T> Decision<T> decide(List<T> variants) {
        return decide(variants, false);
    }

    /**
     * @param <T> Could be numbers, strings, booleans, nulls, or nested list/map structure of these
     *           types.
     * @param variants Variants can be any JSON encodeable data structure of arbitrary complexity,
     *                including nested dictionaries, lists, maps, strings, numbers, nulls, and
     *                booleans.
     * @param ordered True means the variants are already in order starting with the best variant.
     * @return A Decision object.
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     */
    public <T> Decision<T> decide(List<T> variants, boolean ordered) {
        return given(null).decide(variants, ordered);
    }

    /**
     * The chosen variant is the one with highest score.
     * @param <T> Could be numbers, strings, booleans, nulls, or nested list/map structure of these
     *           types.
     * @param variants Variants can be any JSON encodeable data structure of arbitrary complexity,
     *                including nested dictionaries, lists, maps, strings, numbers, nulls, and
     *                booleans.
     * @param scores Scores of the variants.
     * @return A Decision object.
     * @throws IllegalArgumentException Thrown if variants or scores is null or empty; Thrown if
     * variants.size() != scores.size().
     */
    public <T> Decision<T> decide(List<T> variants, List<Double> scores) {
        return given(null).decide(variants, scores);
    }

    /**
     * The variadic version of whichFrom(variants).
     * @param <T> Could be numbers, strings, booleans, nulls, or nested list/map structure of these
     *           types.
     * @param variants A variant can be any JSON encodeable data structure of arbitrary complexity,
     *                 including nested dictionaries, lists, maps, strings, numbers, nulls, and booleans.
     * @return Returns the chosen variant
     * @throws IllegalArgumentException Thrown if variants number is 0.
     */
    @SafeVarargs
    public final <T> T which(T... variants) {
        return given(null).which(variants);
    }

    /**
     * A shorthand of decide(variants).get()
     * @param <T> Could be numbers, strings, booleans, nulls, or nested list/map structure of these
     *           types.
     * @param variants See chooseFrom().
     * @return Returns the chosen variant
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     */
    public <T> T whichFrom(List<T> variants) {
        return given(null).whichFrom(variants);
    }

    /**
     * A shorthand of decide(variants).ranked()
     * @param <T> Could be numbers, strings, booleans, nulls, or nested list/map structure of these
     *           types.
     * @param variants See chooseFrom().
     * @return Ranked variants list starting with the best.
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     */
    public <T> List<T> rank(List<T> variants) {
        return given(null).rank(variants);
    }

    /**
     * Generates all combinations of variants from the variantMap, and choose the best one.
     * @param variantMap The value of the variantMap are expected to be lists of any JSON encodeable
     *                   data structure of arbitrary complexity. If they are not lists, they are
     *                   automatically wrapped as a list containing a single item.
     *                   So optimize({"style":["bold", "italic"], "size":3}) is equivalent to
     *                   optimize({"style":["bold", "italic"], "size":[3]})
     * @return Returns the chosen variant
     * @throws IllegalArgumentException Thrown if the variants to choose from is nil or empty;
     * Thrown if variantMap values
     * are all empty lists.
     */
    public Map<String, Object> optimize(Map<String, ?> variantMap) {
        return given(null).optimize(variantMap);
    }

    /**
     * A handy alternative of optimize(variantMap) that converts the chosen map object to a
     * POJO using Gson.
     * @param <T> Type of POJO.
     * @param variantMap The value of the variantMap are expected to be lists of any JSON encodeable
     *                   data structure of arbitrary complexity. If they are not lists, they are
     *                   automatically wrapped as a list containing a single item.
     *                   So optimize({"style":["bold", "italic"], "size":3}) is equivalent to
     *                   optimize({"style":["bold", "italic"], "size":[3]})
     * @param classOfT The class of the POJO.
     * @return Returns the chosen variant
     * @throws IllegalArgumentException Thrown if the variants to choose from is nil or empty;
     * Thrown if variantMap values
     * are all empty lists.
     */
    public <T> T optimize(Map<String, ?> variantMap, Class<T> classOfT) {
        return given(null).optimize(variantMap, classOfT);
    }

    /**
     * Generates all combinations of variants from the variantMap. An example here might be more
     * expressive:
     * fullFactorialVariants({"style":["bold", "italic"], "size":[3, 5]}) returns
     * [
     *     {"style":"bold", "size":3},
     *     {"style":"italic", "size":3},
     *     {"style":"bold", "size":5},
     *     {"style":"italic", "size":5}
     * ]
     * @param variantMap The values of the variant map are expected to be lists of any JSON
     *                   encodeable data structure of arbitrary complexity. If they are not lists,
     *                   they are automatically wrapped as a list containing a single item.
     *                   So fullFactorialVariants({"style":["bold", "italic"], "size":3}) is
     *                   equivalent to fullFactorialVariants({"style":["bold", "italic"], "size":[3]})
     * @return Returns the full factorial combinations of key and values specified by the input variant map.
     * @throws IllegalArgumentException Thrown if variantMap is nil or empty; Thrown if variantMap values
     * are all empty lists.
     * @hidden
     */
    protected static List<Map<String, Object>> fullFactorialVariants(Map<String, ?> variantMap) {
        if(variantMap == null || variantMap.size() <= 0) {
            throw new IllegalArgumentException("variantMap can't be null or empty");
        }

        List<String> allKeys = new ArrayList<>();

        List<List<?>> categories = new ArrayList<>();
        for(Map.Entry<String, ?> entry : variantMap.entrySet()) {
            if(entry.getValue() instanceof List) {
                if(((List<?>)entry.getValue()).size() > 0) {
                    categories.add((List<?>) entry.getValue());
                    allKeys.add(entry.getKey());
                }
            } else {
                categories.add(Collections.singletonList(entry.getValue()));
                allKeys.add(entry.getKey());
            }
        }
        if(categories.size() <= 0) {
            throw new IllegalArgumentException("variantMap values are all empty lists.");
        }

        List<Map<String, Object>> combinations = new ArrayList<>();
        for(int i = 0; i < categories.size(); ++i) {
            List<?> category = categories.get(i);
            List<Map<String, Object>> newCombinations = new ArrayList<>();
            for(int m = 0; m < category.size(); ++m) {
                if(combinations.size() == 0) {
                    Map<String, Object> newVariant = new HashMap<>();
                    newVariant.put(allKeys.get(i), category.get(m));
                    newCombinations.add(newVariant);
                } else {
                    for(int n = 0; n < combinations.size(); ++n) {
                        Map<String, Object> newVariant = new HashMap<>(combinations.get(n));
                        newVariant.put(allKeys.get(i), category.get(m));
                        newCombinations.add(newVariant);
                    }
                }
            }
            combinations = newCombinations;
        }

        return combinations;
    }

    /**
     * Perform low level tracking of the variant. This is considered a tracked decision for the
     * purposes of updating the decision_id for DecisionModel.addReward(). Only use this if
     * necessary, for example for scoring purposes where no decision is made.
     * @return Returns the tracked decision id.
     * @hidden
     */
    protected String track(Object variant, List<?> runnersUp, Object sample, int samplePoolSize) {
        return given(null).track(variant, runnersUp, sample, samplePoolSize);
    }

    /**
     * @param <T> Could be numbers, strings, booleans, nulls, or nested list/map structure of these
     *           types.
     * @param variants Variants can be any JSON encodeable data structure of arbitrary complexity,
     *                including nested dictionaries, lists, maps, strings, numbers, nulls, and
     *                booleans.
     * @return A Decision object.
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     * @deprecated Remove in 8.0; use {@link #decide(List)} instead.
     */
    @Deprecated
    public <T> Decision<T> chooseFrom(List<T> variants) {
        return given(null).chooseFrom(variants);
    }

    /**
     * @param <T> Could be numbers, strings, booleans, nulls, or nested list/map structure of these
     *           types.
     * @param variants Variants can be any JSON encodeable data structure of arbitrary complexity,
     *                 including nested dictionaries, lists, maps, strings, numbers, nulls, and
     *                 booleans.
     * @param scores Scores of the variants.
     * @return A Decision object which has the variant with highest score as the best variant.
     * @throws IllegalArgumentException Thrown if variants or scores is null or empty; Thrown if
     * variants.size() != scores.size().
     * @deprecated Remove in 8.0; use {@link #decide(List, List)} instead.
     */
    @Deprecated
    public <T> Decision<T> chooseFrom(List<T> variants, List<Double> scores) {
        return given(null).chooseFrom(variants, scores);
    }

    /**
     * This method is an alternative of chooseFrom(). An example here might be more expressive:
     * optimize({"style":["bold", "italic"], "size":[3, 5]})
     *       is equivalent to
     * chooseFrom([
     *      {"style":"bold", "size":3},
     *      {"style":"italic", "size":3},
     *      {"style":"bold", "size":5},
     *      {"style":"italic", "size":5},
     * ])
     * @param variants Variants can be any JSON encodeable data structure of arbitrary complexity
     *                 like chooseFrom(). The value of the dictionary is expected to be a list.
     *                 If not, it would be automatically wrapped as a list containing a single item.
     *                 So chooseMultivariate({"style":["bold", "italic"], "size":3}) is equivalent to
     *                 chooseMultivariate({"style":["bold", "italic"], "size":[3]})
     * @return A Decision object.
     * @deprecated Remove in 8.0; use decide({@link #fullFactorialVariants(Map)})
     */
    @Deprecated
    public Decision<Map<String, Object>> chooseMultivariate(Map<String, ?> variants) {
        return given(null).chooseMultivariate(variants);
    }

    /**
     * @param variants See chooseFrom()
     * @return A Decision object which has the first variant as the best.
     * @deprecated Remove in 8.0; use {@link #decide(List, boolean)}(ordered = true) instead.
     */
    @Deprecated
    public <T> Decision<T> chooseFirst(List<T> variants) {
        return given(null).chooseFirst(variants);
    }

    /**
     * A shorthand of chooseFirst().get().
     * @param variants See chooseFrom().
     * @return Returns the first variant.
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     * @deprecated Remove in 8.0.
     */
    @Deprecated
    public <T> T first(List<T> variants) {
        return given(null).first(variants);
    }

    /**
     * An alternative of first(list).
     * @param variants See chooseFrom().
     * @return Returns the first variant.
     * @throws IllegalArgumentException Thrown if variants number is 0.
     * @deprecated Remove in 8.0.
     */
    @Deprecated
    @SafeVarargs
    public final <T> T first(T... variants) {
        return given(null).first(variants);
    }

    /**
     * Choose a random variant.
     * @param variants See chooseFrom()
     * @return A Decision object containing a random variant as the decision.
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     * @deprecated Remove in 8.0.
     */
    @Deprecated
    public <T> Decision<T> chooseRandom(List<T> variants) {
        return given(null).chooseRandom(variants);
    }

    /**
     * A shorthand of chooseRandom(variants).get()
     * @param variants See chooseFrom().
     * @return A random variant.
     * @throws IllegalArgumentException Thrown if variants is null or empty.
     * @deprecated Remove in 8.0.
     */
    @Deprecated
    public <T> T random(List<T> variants) {
        return given(null).random(variants);
    }

    /**
     * An alternative of random(List).
     * @param variants See chooseFrom().
     * @return A random variant.
     * @throws IllegalArgumentException Thrown if variants number is 0.
     * @deprecated Remove in 8.0.
     */
    @Deprecated
    @SafeVarargs
    public final <T> T random(T... variants) {
        return given(null).random(variants);
    }

    /**
     * If this method is called before the model is loaded, or errors occurred
     * while loading the model file, a randomly generated list of descending
     * Gaussian scores is returned.
     * @param variants Variants can be any JSON encodeable data structure of arbitrary complexity,
     *                 including nested maps, arrays, strings, numbers, nulls, and booleans.
     * @param givens Additional context info that will be used with each of the variants to
     *               calculate the score, including the givens passed in through
     *               DecisionModel.given(givens) and the givens provided by the AppGivensProvider or
     *               other custom GivensProvider.
     * @throws IllegalArgumentException Thrown if variants is null or empty
     * @return scores of the variants
     * @hidden
     */
    protected List<Double> scoreInternal(List<?> variants, Map<String, ?> givens) {
        if(variants == null || variants.size() <= 0) {
            throw new IllegalArgumentException("variants can't be null or empty");
        }

        if(predictor == null) {
            // When tracking a decision like this:
            // DecisionModel("model_name").chooseFrom(variants).get()
            // The model is not loaded. In this case, we return the scores quietly without logging an error.
            // IMPLog.e(Tag, "model is not loaded, a randomly generated list of Gaussian numbers is returned");
            return ModelUtils.generateDescendingGaussians(variants.size());
        }

        List<Double> result = new ArrayList<>();
        List<FVec> encodedFeatures = featureEncoder.encodeVariants(variants, givens);
        for (FVec fvec : encodedFeatures) {
            if(enableTieBreaker) {
                // add a very small random number to randomly break ties
                double smallNoise = Math.random() * Math.pow(2, -23);
                result.add((double) predictor.predictSingle(fvec) + smallNoise);
            } else {
                result.add((double) predictor.predictSingle(fvec));
            }
        }

        return result;
    }

    /**
     * This method should only be called on Android platform.
     * Adds the reward value to the most recent Decision for this model name for this installation.
     * The most recent Decision can be from a different DecisionModel instance or a previous session
     * as long as they have the same model name. If no previous Decision is found, the reward will
     * be ignored.
     * @param reward the reward to add. Must not be NaN, or Infinity.
     * @throws IllegalArgumentException Thrown if `reward` is NaN or Infinity
     * @throws IllegalStateException Thrown if trackURL is null, or called on non-Android platform.
     */
    public void addReward(double reward) {
        if(Double.isInfinite(reward) || Double.isNaN(reward)) {
            throw new IllegalArgumentException("reward must not be NaN or infinity");
        }

        if(DecisionTracker.persistenceProvider == null) {
            throw new IllegalStateException("DecisionModel.addReward() is only available on Android.");
        }

        if(tracker == null) {
            throw new IllegalStateException("trackURL can't be null when calling addReward()");
        }

        tracker.addRewardForModel(modelName, reward);
    }

    /**
     * Add reward for the provided decisionId
     * @param reward reward for the decision.
     * @param decisionId unique id of a decision.
     * @throws IllegalArgumentException Thrown if decisionId is null or empty; Thrown if reward
     * is NaN or Infinity.
     * @throws IllegalStateException Thrown if trackURL is null.
     * Adds the reward to a specific decision
     */
    public void addReward(double reward, String decisionId) {
        if(Double.isInfinite(reward) || Double.isNaN(reward)) {
            throw new IllegalArgumentException("reward must not be NaN or infinity");
        }

        if(Utils.isEmpty(decisionId)) {
            throw new IllegalArgumentException("decisionId can't be null or empty");
        }

        if(tracker == null) {
            throw new IllegalStateException("trackURL can't be null when calling addReward()");
        }

        tracker.addRewardForDecision(modelName, decisionId, reward);
    }

    /**
     * This method is likely to be changed in the future. Try not to use it in your code.
     * @param variants A list of variants to be ranked.
     * @param scores Scores of the variants.
     * @return a list of the variants ranked from best to worst by scores
     * @throws IllegalArgumentException Thrown if variants or scores is null; Thrown if
     * variants.size() not equal to scores.size().
     * @hidden
     */
    protected static <T> List<T> rank(List<T> variants, List<Double> scores) {
        if(variants == null || scores == null) {
            throw new IllegalArgumentException("variants or scores can't be null");
        }

        if(variants.size() != scores.size()) {
            throw new IllegalArgumentException("variants.size() must be equal to scores.size()");
        }

        Integer[] indices = new Integer[variants.size()];
        for(int i = 0; i < variants.size(); ++i) {
            indices[i] = i;
        }

        Arrays.sort(indices, Collections.reverseOrder(new Comparator<>() {
            public int compare(Integer obj1, Integer obj2) {
                return Double.compare(scores.get(obj1), scores.get(obj2));
            }
        }));

        List<T> result = new ArrayList<>(variants.size());
        for(int i = 0; i < indices.length; ++i) {
            result.add(variants.get(indices[i]));
        }

        return result;
    }

    public interface LoadListener {
        void onLoad(DecisionModel decisionModel);

        void onError(IOException e);
    }

    private int getSeq() {
        return seq.getAndIncrement();
    }

    private boolean isValidModelName(String modelName) {
        return modelName != null && modelName.matches("^[a-zA-Z0-9][\\w\\-.]{0,63}$");
    }

    /** @hidden */
    protected static void clearInstances() {
        instances.clear();
    }

    /** @hidden */
    protected static int sizeOfInstances() {
        return instances.size();
    }
}
