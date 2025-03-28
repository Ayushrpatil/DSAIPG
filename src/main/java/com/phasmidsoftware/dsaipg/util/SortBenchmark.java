/*
 * Copyright (c) 2024. Robin Hillyard
 */
package com.phasmidsoftware.dsaipg.util;

import com.phasmidsoftware.dsaipg.sort.*;
import com.phasmidsoftware.dsaipg.sort.classic.BucketSort;
import com.phasmidsoftware.dsaipg.sort.counting.LSDStringSort;
import com.phasmidsoftware.dsaipg.sort.counting.MSDStringSort;
import com.phasmidsoftware.dsaipg.sort.elementary.*;
import com.phasmidsoftware.dsaipg.sort.linearithmic.TimSort;
import com.phasmidsoftware.dsaipg.sort.linearithmic.*;
import com.phasmidsoftware.dsaipg.sort.InstrumentedComparableHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.phasmidsoftware.dsaipg.sort.InstrumentedComparatorHelper.AT;
import static com.phasmidsoftware.dsaipg.sort.linearithmic.MergeSort.MERGESORT;
import static com.phasmidsoftware.dsaipg.util.Config_Benchmark.isInstrumented;
import static com.phasmidsoftware.dsaipg.util.SortBenchmarkHelper.*;
import static com.phasmidsoftware.dsaipg.util.Utilities.formatWhole;

public class SortBenchmark {

    public static void main(String[] args) throws IOException {
        Config config = Config.load(SortBenchmark.class);
        logger.info("!!!!!!!!!!!!!!!!!!!! SortBenchmark Start !!!!!!!!!!!!!!!!!!!!\n");
        logger.info("SortBenchmark.main: version " + config.get("sortbenchmark", "version") + " with word counts: " + Arrays.toString(args));
        if (args.length == 0) logger.warn("No word counts specified on the command line");
        new SortBenchmark(config).doMain(args);
    }

    void doMain(String[] args) {
        sortStrings(getWordCounts(args));
        sortIntegers(getWordCounts(args));
    }

    public SortBenchmark(Config config) {
        this.config = config;
    }

    void sortIntegers(Stream<Long> wordCounts) {
        wordCounts.forEach(this::runIntegerSorts);
    }

    void runIntegerSorts(long N) {
        if (N > Integer.MAX_VALUE) throw new SortException("number of elements is too large");
        double totalWork = getTotalWork(N, config, "benchmarkintegersorters");
        if (isConfigBenchmarkIntegerSorter("shellsort"))
            sortIntegersByShellSort((int) N, 12 * estimateRuns(totalWork, Math.pow(N, 4.0 / 3)));
        if (isConfigBenchmarkIntegerSorter("bucketsort"))
            runIntegerBucketSort((int) N, estimateRuns(totalWork * 2, N));
        if (isConfigBenchmarkIntegerSorter("quicksort"))
            runIntegerQuickSort((int) N, 10 * estimateRuns(totalWork, Math.log(N) * N));
    }

    public void sortLocalDateTimes(final int n, Config config) throws IOException {
        logger.info("Beginning LocalDateTime sorts");
        Supplier<LocalDateTime[]> localDateTimeSupplier = () -> generateRandomLocalDateTimeArray(n);
        Helper<ChronoLocalDateTime<?>> helper = new NonInstrumentingComparableHelper<>("DateTimeHelper", config);
        final LocalDateTime[] localDateTimes = generateRandomLocalDateTimeArray(n);

        if (isConfigBenchmarkDateSorter("timsort"))
            logger.info(benchmarkFactory("ProcessingSort LocalDateTimes using Arrays::sort (TimSort)", Arrays::sort, null).runFromSupplier(localDateTimeSupplier, 100) + "ms");

        if (isConfigBenchmarkDateSorter("timsort")) {
            logger.info(benchmarkFactory("Repeat ProcessingSort LocalDateTimes using timSort::mutatingSort", new TimSort<>(helper)::mutatingSort, null).runFromSupplier(localDateTimeSupplier, 100) + "ms");
            runDateTimeSortBenchmark(LocalDateTime.class, localDateTimes, n, 100);
        }
    }

    public static Collection<String> getLeipzigWords(String line) {
        return getWords(regexLeipzig, line);
    }

    void benchmarkStringSorters(String[] words, int nWords) {
        double totalWork = getTotalWork(nWords, config, BENCHMARKSTRINGSORTERS);
        logger.info("benchmarkStringSorters: sorting " + formatWhole(nWords) + " words" + (isInstrumented(config) ? " and instrumented" : "") + " with total work (for estimating runs): " + totalWork);
        if (isInstrumented(config))
            logger.info("    normalization of statistics is based on n ln n");
        Random random = new Random();
        int nRunsLinearithmic = estimateRuns(totalWork, minComparisons(nWords));
        int nRunsLinear = estimateRuns(totalWork, 15.0 * nWords);
        int nRunsBucket = estimateRuns(totalWork, 2.0 * nWords + 0.5 * nWords * nWords / BucketSort.DIGRAPHS_SIZE);

        if (isConfigBenchmarkStringSorter("puresystemsort") && nRunsLinearithmic > 0)
            runPureSystemSortBenchmark(words, nWords, nRunsLinearithmic, random);

        if (isConfigBenchmarkStringSorter("bucketsort") && nRunsBucket > 0)
            try (SortWithHelper<String> sorter = BucketSort.CaseIndependentBucketSort(BucketSort::classifyStringDigraph, BucketSort.DIGRAPHS_SIZE, nWords, config)) {
                runStringSortBenchmark(words, nWords, nRunsBucket, sorter, timeLoggersLinear);
            }

        if (isConfigBenchmarkStringSorter("LSD") && nRunsLinear > 0) {
            int nRuns = nRunsLinear * 5;
            try (SortWithHelper<String> sorter = new LSDStringSort(nWords, 20, String::compareTo, nRuns, config)) {
                runStringSortBenchmark(words, nWords, nRuns, sorter, timeLoggersLinear);
            }
        }

        if (isConfigBenchmarkStringSorter("MSD") && nRunsLinear > 0) {
            int nRuns = nRunsLinear * 5;
            try (SortWithHelper<String> sorter = new MSDStringSort(CodePointMapper.ASCIIExt, nWords, nRuns, config)) {
                runStringSortBenchmark(words, nWords, nRuns, sorter, timeLoggersLinear);
            }
        }

        if (isConfigBenchmarkStringSorter("timsort") && nRunsLinearithmic > 0)
            try (SortWithHelper<String> sorter = TimSort.CaseInsensitiveSort(nWords, config)) {
                runStringSortBenchmark(words, nWords, nRunsLinearithmic * 2, sorter, timeLoggersLinearithmic);
            }

        if (isConfigBenchmarkStringSorter(MERGESORT)) {
            try (SortWithHelper<String> sorter = new MergeSort<>(nWords, nRunsLinearithmic * 4, config)) {
                runStringSortBenchmark(words, nWords, nRunsLinearithmic * 4, sorter, timeLoggersLinearithmic);

                if (sorter.getHelper() instanceof InstrumentedComparableHelper) {
                    InstrumentedComparableHelper<String> helper = (InstrumentedComparableHelper<String>) sorter.getHelper();
                    logger.info("Instrumentation for MergeSort, size=" + nWords + ":");
                    logger.info("Comparisons: " + helper.getCompares());
                    logger.info("Swaps: " + helper.getSwaps());
                    logger.info("Hits: " + helper.getHits());
                    logger.info("Copies: " + helper.getCopies());
                    logger.info("Memory usage (estimated): N/A");
                }
            }
        }

        if (isConfigBenchmarkStringSorter("quicksort3way") && nRunsLinearithmic > 0)
            try (SortWithHelper<String> sorter = new QuickSort_3way<>(nWords, nRunsLinearithmic, config)) {
                runStringSortBenchmark(words, nWords, nRunsLinearithmic * 3, sorter, timeLoggersLinearithmic);
            }

        if (isConfigBenchmarkStringSorter("quicksortDualPivot") && nRunsLinearithmic > 0) {
            try (SortWithHelper<String> sorter = new QuickSort_DualPivot<>(nWords, nRunsLinearithmic, config)) {
                runStringSortBenchmark(words, nWords, nRunsLinearithmic * 4, sorter, timeLoggersLinearithmic);

                if (sorter.getHelper() instanceof InstrumentedComparableHelper) {
                    InstrumentedComparableHelper<String> helper = (InstrumentedComparableHelper<String>) sorter.getHelper();
                    logger.info("Instrumentation for QuickSort Dual Pivot, size=" + nWords + ":");
                    logger.info("Comparisons: " + helper.getCompares());
                    logger.info("Swaps: " + helper.getSwaps());
                    logger.info("Hits: " + helper.getHits());
                    logger.info("Copies: " + helper.getCopies());
                    logger.info("Memory usage (estimated): N/A");
                }
            }
        }

        if (isConfigBenchmarkStringSorter("quicksort") && nRunsLinearithmic > 0)
            try (SortWithHelper<String> sorter = new QuickSort_Basic<>(nWords, nRunsLinearithmic, config)) {
                runStringSortBenchmark(words, nWords, nRunsLinearithmic * 3, sorter, timeLoggersLinearithmic);
            }

        if (isConfigBenchmarkStringSorter("heapsort") && nRunsLinearithmic > 0) {
            try (SortWithHelper<String> sorter = new HeapSort<>(nWords, nRunsLinearithmic, config)) {
                runStringSortBenchmark(words, nWords, nRunsLinearithmic * 3, sorter, timeLoggersLinearithmic);

                if (sorter.getHelper() instanceof InstrumentedComparableHelper) {
                    InstrumentedComparableHelper<String> helper = (InstrumentedComparableHelper<String>) sorter.getHelper();
                    logger.info("Instrumentation for HeapSort, size=" + nWords + ":");
                    logger.info("Comparisons: " + helper.getCompares());
                    logger.info("Swaps: " + helper.getSwaps());
                    logger.info("Hits: " + helper.getHits());
                    logger.info("Copies: " + helper.getCopies());
                    logger.info("Memory usage (estimated): N/A");
                }
            }
        }

        if (isConfigBenchmarkStringSorter("introsort") && nRunsLinearithmic > 0)
            try (SortWithHelper<String> sorter = new IntroSort<>(nWords, nRunsLinearithmic, config)) {
                runStringSortBenchmark(words, nWords, nRunsLinearithmic * 3, sorter, timeLoggersLinearithmic);
            }

        if (isConfigBenchmarkStringSorter("randomsort") && nRunsLinearithmic > 0)
            try (SortWithHelper<String> sorter = new RandomSort<>(nWords, config)) {
                runStringSortBenchmark(words, nWords, nRunsLinearithmic, sorter, timeLoggersLinearithmic);
            }

        if (isConfigBenchmarkStringSorter("shellsort")) {
            int nRunsSubQuadratic = estimateRuns(totalWork, Math.pow(nWords, 4.0 / 3) / 2);
            if (nRunsSubQuadratic > 0)
                try (SortWithHelper<String> sorter = new ShellSort<>(4, nWords, nRunsSubQuadratic, config)) {
                    runStringSortBenchmark(words, nWords, nRunsSubQuadratic, sorter, timeLoggersSubQuadratic);
                }
        }

        if (isConfigBenchmarkStringSorter("insertionsort") || isConfigBenchmarkStringSorter("insertionsortopt") || isConfigBenchmarkStringSorter("bubblesort")) {
            double inversions = meanInversions(nWords);
            int nRunsQuadraticQuick = estimateRuns(totalWork * 15, inversions);
            int nRunsQuadraticSlow = estimateRuns(totalWork * 4, inversions);

            if (isConfigBenchmarkStringSorter("insertionsortopt") && nRunsQuadraticQuick > 0)
                try (SortWithHelper<String> sorter = new InsertionSortOpt<>(InsertionSortOpt.DESCRIPTION, nWords, nRunsQuadraticQuick, config)) {
                    runStringSortBenchmark(words, nWords, nRunsQuadraticQuick, sorter, timeLoggersQuadratic);
                }

            if (isConfigBenchmarkStringSorter("insertionsort") && nRunsQuadraticQuick > 0)
                try (SortWithHelper<String> sorter = new InsertionSort<>(InsertionSort.DESCRIPTION, nWords, nRunsQuadraticQuick, config)) {
                    runStringSortBenchmark(words, nWords, nRunsQuadraticQuick, sorter, timeLoggersQuadratic);
                }

            if (isConfigBenchmarkStringSorter("bubblesort") && nRunsQuadraticSlow > 0)
                try (SortWithHelper<String> sorter = new BubbleSort<>(nWords, nRunsQuadraticSlow, config)) {
                    runStringSortBenchmark(words, nWords, nRunsQuadraticSlow, sorter, timeLoggersQuadratic);
                }
        }
    }

    private static double getTotalWork(long n, Config config, final String configSection) {
        long z = config.getLong(configSection, "totalwork", 100_000_000L);
        long x = n / 512_000 + 1;
        return (double) z * x;
    }

    private int estimateRuns(double totalWork, double workPerRun) {
        long result = Utilities.round(totalWork / workPerRun);
        if (result >= 0 && result < Integer.MAX_VALUE)
            if (result < 10_000_000)
                return (int) result;
            else
                throw new SortException("estimated number of runs is too large (max is 10 million): " + result + ". Reduce the value of totalwork accordingly");
        else
            throw new RuntimeException("estimated number of runs is not a positive Integer: " + result);
    }

    private void runIntegerBucketSort(int N, final int runs) {
        int bucketSize = config.getInt(BENCHMARKINTEGERSORTERS, "bucketsize", 16);
        int buckets = (N + bucketSize - 1) / bucketSize;
        BucketSort<Integer> sorter = new BucketSort<>(null, buckets, N, config);
        Helper<Integer> helper = sorter.getHelper();
        helper.init(N);
        Integer[] xs = helper.random(N, Integer.class, r -> r.nextInt(1000));
        runIntegerSortBenchmark(xs, N, runs, sorter, null, timeLoggersLinearithmic);
        helper.close();
    }

    private static void runPureSystemSortBenchmark(String[] words, int nWords, int nRuns, Random random) {
        Benchmark<String[]> benchmark = new Benchmark_Timer<>("SystemSort", null, Arrays::sort, null);
        doPureBenchmark(words, nWords, nRuns, random, benchmark);
    }

    private void sortIntegersByShellSort(int N, int runs) {
        int m = config.getInt(BENCHMARKINTEGERSORTERS, "mode", 4);
        SortWithHelper<Integer> sorter = new ShellSort<>(m, N, runs, config);
        Integer[] numbers = sorter.getHelper().random(Integer.class, Random::nextInt);
        runIntegerSortBenchmark(numbers, N, runs, sorter, sorter::preProcess, timeLoggersSubQuadratic);
    }

    private void runIntegerQuickSort(int N, final int runs) {
        SortWithHelper<Integer> sorter = new QuickSort_DualPivot<>(N, runs, config);
        Integer[] numbers = sorter.getHelper().random(Integer.class, Random::nextInt);
        runIntegerSortBenchmark(numbers, N, runs, sorter, sorter::preProcess, timeLoggersLinearithmic);
    }

    private void sortStrings(Stream<Long> wordCounts) {
        logger.info("Beginning String sorts");
        wordCounts.forEach(this::doLeipzigBenchmarkEnglish);
    }

    private void doLeipzigBenchmarkEnglish(long N) {
        if (N > Integer.MAX_VALUE) throw new SortException("number of elements is too large");
        int x = (int) N;
        logger.info("############################### " + x + " words ###############################");
        String resource = "eng-uk_web_2002_" + (x < 50000 ? "10K" : "100K") + "-sentences.txt";
        try {
            benchmarkStringSorters(getWords(resource, SortBenchmark::getLeipzigWords), x);
        } catch (FileNotFoundException e) {
            logger.warn("Unable to find resource: " + resource + "because:", e);
        } catch (Exception e) {
            logger.warn("Unable to run benchmark with N: " + N + "because:", e);
        }
    }

    static void runStringSortBenchmark(String[] words, int nWords, int nRuns, SortWithHelper<String> sorter, UnaryOperator<String[]> preProcessor, TimeLogger[] timeLoggers) {
        logger.info("****************************** String sort: " + nRuns + " runs of " + nWords + " " + sorter.getDescription() + " ******************************");
        new SorterBenchmark<>(String.class, preProcessor, sorter, words, nRuns, timeLoggers).run(getDescription(nWords, sorter), nWords);
        sorter.close();
    }

    public static void runStringSortBenchmark(String[] words, int nWords, int nRuns, SortWithHelper<String> sorter, TimeLogger[] timeLoggers) {
        sorter.getHelper().init(nWords, nRuns);
        try (Stopwatch stopwatch = new Stopwatch()) {
            runStringSortBenchmark(words, nWords, nRuns, sorter, sorter::preProcess, timeLoggers);
            logger.info("************************************************************ (" + stopwatch.lap() / 1000.0 + " sec.)");
        }
    }

    static void runIntegerSortBenchmark(Integer[] numbers, int n, int nRuns, SortWithHelper<Integer> sorter, UnaryOperator<Integer[]> preProcessor, TimeLogger[] timeLoggers) {
        logger.info("****************************** Integer sort: " + n + " " + sorter.getDescription() + " ******************************");
        try (Stopwatch stopwatch = new Stopwatch()) {
            new SorterBenchmark<>(Integer.class, preProcessor, sorter, numbers, nRuns, timeLoggers).run(getDescription(n, sorter), n);
            sorter.close();
            logger.info("************************************************************ (" + stopwatch.lap() / 1000.0 + " sec.)");
        }
    }

    public static final String BENCHMARKSTRINGSORTERS = "benchmarkstringsorters";
    public static final TimeLogger TIME_LOGGER_RAW = new TimeLogger("Raw time per run {mSec}: ", null);

    public final static TimeLogger[] timeLoggersLinearithmic = {
            TIME_LOGGER_RAW,
            new TimeLogger("Normalized time per run {n log n}: ", SortBenchmark::minComparisons)
    };

    public final static TimeLogger[] timeLoggersLinear = {
            TIME_LOGGER_RAW,
            new TimeLogger("Normalized time per run {n}: ", n -> n * 1.0)
    };

    final static TimeLogger[] timeLoggersQuadratic = {
            TIME_LOGGER_RAW,
            new TimeLogger("Normalized time per run {n^2}: ", SortBenchmark::meanInversions)
    };

    final static TimeLogger[] timeLoggersSubQuadratic = {
            TIME_LOGGER_RAW,
            new TimeLogger("Normalized time per run {n^(4/3)}: ", n -> Math.pow(n, 5.0 / 4))
    };

    final static LazyLogger logger = new LazyLogger(SortBenchmark.class);

    static double minComparisons(int n) {
        double lgN = Utilities.lg(n);
        return n * (lgN - LgE) + lgN / 2 + 1.33;
    }

    static double meanInversions(int n) {
        return 0.25 * n * (n - 1);
    }

    private static Collection<String> lineAsList(String line) {
        List<String> words = new ArrayList<>();
        words.add(line);
        return words;
    }

    private static Benchmark<LocalDateTime[]> benchmarkFactory(String description, Consumer<LocalDateTime[]> sorter, Consumer<LocalDateTime[]> checker) {
        return new Benchmark_Timer<>(
                description,
                (xs) -> Arrays.copyOf(xs, xs.length),
                sorter,
                checker
        );
    }

    private static void doPureBenchmark(String[] words, int nWords, int nRuns, Random random, Benchmark<String[]> benchmark) {
        final double time = benchmark.runFromSupplier(() -> Utilities.fillRandomArray(String.class, random, nWords, r -> words[r.nextInt(words.length)]), nRuns);
        for (TimeLogger timeLogger : timeLoggersLinearithmic) timeLogger.log("pure benchmark", time, nWords);
    }

    private static Stream<Long> getWordCounts(String[] args) {
        return Arrays.stream(args).map(SortBenchmark::parseInt);
    }

    static long parseInt(String w) {
        long result = 1L;
        String expression = w.replaceAll("[gG]", "mk").replaceAll("[mM]", "kk").replaceAll("[kK]", "*1024");
        for (String split : expression.split("\\*")) result *= Integer.parseInt(split);
        return result;
    }

    private void runMergeSortBenchmark(String[] words, int nWords, int nRuns, Config config) {
        try (SortWithComparableHelper<String> sorter = new MergeSort<>(nWords, nRuns, config)) {
            runStringSortBenchmark(words, nWords, nRuns, sorter, timeLoggersLinearithmic);
        }
    }

    private static <X> String getDescription(int n, Sort<X> sorter) {
        return n + AT + sorter.getDescription();
    }

    @SuppressWarnings("SameParameterValue")
    private void runDateTimeSortBenchmark(Class<?> tClass, ChronoLocalDateTime<?>[] dateTimes, int N, int m) throws IOException {
        final SortWithHelper<ChronoLocalDateTime<?>> sorter = new TimSort<>();
        logger.info("****************************** DateTime sort: " + N + " " + sorter.getDescription() + " ******************************");
        @SuppressWarnings("unchecked") final SorterBenchmark<ChronoLocalDateTime<?>> sorterBenchmark = new SorterBenchmark<>((Class<ChronoLocalDateTime<?>>) tClass, (xs) -> Arrays.copyOf(xs, xs.length), sorter, dateTimes, m, timeLoggersLinearithmic);
        sorterBenchmark.run(getDescription(N, sorter), N);
        sorter.close();
        logger.info("************************************************************");
    }

    private static final double LgE = Utilities.lg(Math.E);

    private boolean isConfigBenchmarkStringSorter(String option) {
        return isConfigBoolean(BENCHMARKSTRINGSORTERS, option);
    }

    private boolean isConfigBenchmarkDateSorter(String option) {
        return isConfigBoolean("benchmarkdatesorters", option);
    }

    private boolean isConfigBenchmarkIntegerSorter(String option) {
        return isConfigBoolean(BENCHMARKINTEGERSORTERS, option);
    }

    private boolean isConfigBoolean(String section, String option) {
        return config.getBoolean(section, option);
    }

    public static final String BENCHMARKINTEGERSORTERS = "benchmarkintegersorters";

    private final Config config;
}