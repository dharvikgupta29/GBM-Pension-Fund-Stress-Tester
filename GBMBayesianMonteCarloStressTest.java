import java.util.Random;

/**
 * GBMBayesianMonteCarloStressTest (Hybrid Pension Fund Simulation)
 *
 * This engine simulates the evolution of a pension fund under uncertainty
 * using:
 *
 * • Geometric Brownian Motion (GBM) baseline returns. • Adaptive drift and
 * volatility based on fund health. • Downside shocks: recession, war, pandemic,
 * black swan. • Upside shocks: bull market bursts. • Monthly pension
 * liabilities. • Beta-Binomial updating of shock frequencies.
 *
 * @author Dharvik Gupta
 *
 * SHOCK SEVERITIES — STATS 3470 ONLY
 * ------------------------------------------------------- All shock severities
 * are generated using ONLY distributions taught in OSU STATS 3470:
 *
 * 1. Exponential(1) 2. Gamma(k,1) = sum of k Exponential(1) RVs 3.
 * Binomial(n,p) scaled severity 4. Negative Binomial(r,p) severity (failures
 * until r successes)
 *
 * Each shock type automatically selects its own severity model internally.
 */

public final class GBMBayesianMonteCarloStressTest {

    private GBMBayesianMonteCarloStressTest() {
    }

    //
    // CLAMP FUNCTION
    //
    private static double clamp(double x, double lo, double hi) {
        if (x < lo) {
            return lo;
        }
        if (x > hi) {
            return hi;
        }
        return x;
    }

    //
    // PERCENTILE of a sorted array (nearest-rank)
    //
    private static double percentile(double[] sorted, double p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index > sorted.length - 1) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }

    //
    // MEAN OF THE WORST p PERCENT (conditional tail). Two funds can share a
    // 5th percentile and still differ a lot in how deep the losses go past it.
    //
    private static double tailMean(double[] sorted, double p) {
        int cutoff = (int) Math.round(p / 100.0 * sorted.length);
        if (cutoff < 1) {
            cutoff = 1;
        }
        double sum = 0.0;
        for (int i = 0; i < cutoff; i++) {
            sum += sorted[i];
        }
        return sum / cutoff;
    }

    //
    // EXPONENTIAL(1): inverse transform sampling
    //
    private static double sampleExponential(Random rng) {
        double u = rng.nextDouble();
        double oneMinusU = 1.0 - u;
        double x = -Math.log(oneMinusU);
        return x;
    }

    //
    // GAMMA(k,1) = sum of k Exponential(1)
    //
    private static double sampleGamma(Random rng, int k) {
        double sum = 0.0;
        for (int i = 0; i < k; i++) {
            sum += sampleExponential(rng);
        }
        return sum;
    }

    //
    // BINOMIAL(n,p) severity scaled by max drop
    //
    private static double sampleBinomialSeverity(Random rng, int n, double p,
            double maxValue) {
        int count = 0;
        for (int i = 0; i < n; i++) {
            double u = rng.nextDouble();
            if (u < p) {
                count++;
            }
        }
        double sev = (double) count / (double) n;
        sev = sev * maxValue;
        return sev;
    }

    //
    // NEGATIVE BINOMIAL(r,p) severity (failures until r successes)
    //
    private static double sampleNegBinSeverity(Random rng, int r, double p,
            double maxValue) {
        int successes = 0;
        int failures = 0;

        while (successes < r) {
            double u = rng.nextDouble();
            if (u < p) {
                successes++;
            } else {
                failures++;
            }
        }

        double ratio = (double) failures / (double) (failures + r);
        if (ratio > 1.0) {
            ratio = 1.0;
        }

        double sev = ratio * maxValue;
        return sev;
    }

    //
    // RECESSION SEVERITY (Negative Binomial)
    //
    private static double recessionSeverity(Random rng, double maxDrop) {
        int r = 2;
        double p = 0.25;
        return sampleNegBinSeverity(rng, r, p, maxDrop);
    }

    //
    // WAR SEVERITY (Binomial)
    //
    private static double warSeverity(Random rng, double maxDrop) {
        int n = 8;
        double p = 0.15;
        return sampleBinomialSeverity(rng, n, p, maxDrop);
    }

    //
    // PANDEMIC SEVERITY (Gamma)
    //
    private static double pandemicSeverity(Random rng, double maxDrop, double gammaCap) {
        int k = 3;
        double g = sampleGamma(rng, k);
        if (g > gammaCap) {
            g = gammaCap;
        }
        double sev = (g / gammaCap) * maxDrop;
        return sev;
    }

    //
    // BLACK SWAN SEVERITY (Negative Binomial)
    //
    private static double blackSwanSeverity(Random rng, double maxDrop) {
        int r = 4;
        double p = 0.18;
        return sampleNegBinSeverity(rng, r, p, maxDrop);
    }

    //
    // BULL BURST SEVERITY (Binomial)
    //
    private static double bullSeverity(Random rng, double maxGain) {
        int n = 8;
        double p = 0.30;
        return sampleBinomialSeverity(rng, n, p, maxGain);
    }

    //
    // MAIN
    //
    public static void main(String[] args) {

        //
        // 1. FUND SETTINGS
        //
        final double initialFund = 1_000_000_000.0;
        final double baseDrift = 0.07;
        final double baseVolatility = 0.10;
        final int years = 10;
        final int months = years * 12;
        final int runs = 1_000_000;
        final double floor = 700_000_000.0;
        final double monthlyPayout = 3_000_000.0;
        final double dt = 1.0 / 12.0;

        //
        // 2. SHOCK PROBABILITIES
        //
        final double baseProbRec = 0.0020;
        final double baseProbWar = 0.0005;
        final double baseProbPan = 0.0005;
        final double baseProbBsw = 0.0003;
        final double baseProbBull = 0.0400;

        //
        // 3. SHOCK MAGNITUDES
        //
        final double maxRecDrop = 0.04;
        final double maxWarDrop = 0.03;
        final double maxPanDrop = 0.06;
        final double maxBswDrop = 0.10;
        final double maxBullGain = 0.03;
        final double maxMonthlyLoss = 0.25;
        final double gammaCap = 5.0;

        //
        // 4. RANDOM ENGINE
        //
        // Seeded so runs are reproducible. Keep it fixed when comparing
        // scenarios so the difference comes from the parameter, not the draws.
        final long SEED = 42L;
        Random rng = new Random(SEED);

        //
        // 5. RESULT ACCUMULATORS
        //
        int insolventRuns = 0;
        double totalFinalValue = 0.0;

        int totalRecHits = 0;
        int totalWarHits = 0;
        int totalPanHits = 0;
        int totalBswHits = 0;

        // Keep every terminal value so percentiles can be reported.
        // The average hides the left tail, which is the whole question here.
        double[] finalValues = new double[runs];

        // Beta prior strength, in pseudo-months. At 3000, one simulated
        // decade (120 months) barely moves the posterior. That is intended:
        // 10 years is not enough to relearn how often pandemics happen.
        final double priorStrength = 3000.0;

        //
        // 6. MONTE CARLO SIMULATION
        //
        for (int run = 0; run < runs; run++) {

            // Beta(alpha, beta) prior on each shock's monthly probability.
            // Reset per run: each path is one possible history, so learning
            // happens inside it, not across the ensemble.
            double alphaRec = baseProbRec * priorStrength;
            double betaRec = (1.0 - baseProbRec) * priorStrength;
            double alphaWar = baseProbWar * priorStrength;
            double betaWar = (1.0 - baseProbWar) * priorStrength;
            double alphaPan = baseProbPan * priorStrength;
            double betaPan = (1.0 - baseProbPan) * priorStrength;
            double alphaBsw = baseProbBsw * priorStrength;
            double betaBsw = (1.0 - baseProbBsw) * priorStrength;

            double probBull = baseProbBull;

            double fund = initialFund;

            int recHits = 0;
            int warHits = 0;
            int panHits = 0;
            int bswHits = 0;

            double drift = baseDrift;
            double volatility = baseVolatility;

            for (int month = 0; month < months; month++) {

                //
                // 6.1 FUND HEALTH (adaptive drift & vol)
                //
                double health = fund / initialFund;
                health = clamp(health, 0.7, 1.3);

                volatility = baseVolatility * (1.0 + (1.0 - health) * 0.4);
                drift = baseDrift * (0.90 + 0.20 * health);

                //
                // 6.2 GBM RETURN
                //
                double mu = (drift - 0.5 * volatility * volatility) * dt;
                double sigma = volatility * Math.sqrt(dt);
                double z = rng.nextGaussian();
                double rtn = Math.exp(mu + sigma * z) - 1.0;

                //
                // 6.3 DOWNSIDE SHOCKS: recession, war, pandemic, black swan
                //
                double totalLoss = 0.0;

                // Current probability = posterior mean = a / (a + b).
                double probRec = alphaRec / (alphaRec + betaRec);
                double probWar = alphaWar / (alphaWar + betaWar);
                double probPan = alphaPan / (alphaPan + betaPan);
                double probBsw = alphaBsw / (alphaBsw + betaBsw);

                // recession
                double u = rng.nextDouble();
                boolean recOccurred = u < probRec;
                if (recOccurred) {
                    double sev = recessionSeverity(rng, maxRecDrop);
                    totalLoss += sev;
                    recHits++;
                }

                // war
                u = rng.nextDouble();
                boolean warOccurred = u < probWar;
                if (warOccurred) {
                    double sev = warSeverity(rng, maxWarDrop);
                    totalLoss += sev;
                    warHits++;
                }

                // pandemic
                u = rng.nextDouble();
                boolean panOccurred = u < probPan;
                if (panOccurred) {
                    double sev = pandemicSeverity(rng, maxPanDrop, gammaCap);
                    totalLoss += sev;
                    panHits++;
                }

                // black swan
                u = rng.nextDouble();
                boolean bswOccurred = u < probBsw;
                if (bswOccurred) {
                    double sev = blackSwanSeverity(rng, maxBswDrop);
                    totalLoss += sev;
                    bswHits++;
                }

                if (totalLoss > maxMonthlyLoss) {
                    totalLoss = maxMonthlyLoss;
                }

                rtn -= totalLoss;

                //
                // 6.4 BULL SHOCK
                //
                double ub = rng.nextDouble();
                if (ub < probBull) {
                    double sev = bullSeverity(rng, maxBullGain);
                    rtn += sev;
                }

                //
                // 6.5 APPLY RETURNS & LIABILITY OUTFLOW
                //
                fund *= (1.0 + rtn);
                fund -= monthlyPayout;

                if (fund <= 0.0) {
                    fund = 0.0;
                    break;
                }

                //
                // 6.6 BAYESIAN UPDATES
                //
                // Each month is a Bernoulli trial. Beta is conjugate to
                // Bernoulli, so the update is exact: +1 to alpha on a hit,
                // +1 to beta on a miss. No learning rate, and no clamping
                // needed since a Beta posterior stays inside (0,1).
                if (recOccurred) {
                    alphaRec += 1.0;
                } else {
                    betaRec += 1.0;
                }
                if (warOccurred) {
                    alphaWar += 1.0;
                } else {
                    betaWar += 1.0;
                }
                if (panOccurred) {
                    alphaPan += 1.0;
                } else {
                    betaPan += 1.0;
                }
                if (bswOccurred) {
                    alphaBsw += 1.0;
                } else {
                    betaBsw += 1.0;
                }
            }

            if (fund < floor) {
                insolventRuns++;
            }

            finalValues[run] = fund;
            totalFinalValue += fund;
            totalRecHits += recHits;
            totalWarHits += warHits;
            totalPanHits += panHits;
            totalBswHits += bswHits;
        }

        //
        // 7. POSTERIOR PROBABILITIES
        //
        double denom = runs * months + 2.0;
        double postRec = (totalRecHits + 1.0) / denom;
        double postWar = (totalWarHits + 1.0) / denom;
        double postPan = (totalPanHits + 1.0) / denom;
        double postBsw = (totalBswHits + 1.0) / denom;

        //
        // 8. SUMMARY OUTPUT
        //
        double avgFinal = totalFinalValue / runs;
        double insolvencyRisk = 100.0 * insolventRuns / runs;

        // One sort gives every percentile and the tail mean.
        java.util.Arrays.sort(finalValues);

        System.out.printf("Simulations Run        : %,d  (seed %d)%n", runs, SEED);
        System.out.printf("Insolvency Probability : %.2f%%%n", insolvencyRisk);

        System.out.println("\nDistribution of Final Value:");
        System.out.printf("   1st percentile : $%,.0f%n", percentile(finalValues, 1));
        System.out.printf("   5th percentile : $%,.0f%n", percentile(finalValues, 5));
        System.out.printf("  Mean worst 5%%   : $%,.0f%n", tailMean(finalValues, 5));
        System.out.printf("  Median          : $%,.0f%n", percentile(finalValues, 50));
        System.out.printf("  Average         : $%,.0f%n", avgFinal);
        System.out.printf("  95th percentile : $%,.0f%n", percentile(finalValues, 95));

        System.out.println("\nPosterior Shock Probabilities (monthly):");
        System.out.printf("  Recession : %.6f%n", postRec);
        System.out.printf("  War       : %.6f%n", postWar);
        System.out.printf("  Pandemic  : %.6f%n", postPan);
        System.out.printf("  Black Swan: %.6f%n", postBsw);

        System.out.println("\nTotal Shock Events Across All Runs:");
        System.out.printf("  Recession : %,d%n", totalRecHits);
        System.out.printf("  War       : %,d%n", totalWarHits);
        System.out.printf("  Pandemic  : %,d%n", totalPanHits);
        System.out.printf("  Black Swan: %,d%n", totalBswHits);
    }
}
