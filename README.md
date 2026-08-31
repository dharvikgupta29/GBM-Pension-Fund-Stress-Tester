# GBM Pension Fund Stress Tester

Monte Carlo engine that estimates how likely a defined-benefit pension fund is to
fall below a funding floor over ten years, when the frequency of the shocks that
could sink it is itself uncertain.

Fund value follows geometric Brownian motion, with drift and volatility that
respond to funding health. Four shock regimes (recession, war, pandemic, black
swan) sit on top of it, and a fixed monthly liability is drawn against the
balance. Each shock's monthly arrival probability is an unknown parameter with a
Beta prior, updated month by month as the simulated history plays out.

Java, no dependencies.

## Running it

```bash
javac GBMBayesianMonteCarloStressTest.java
java GBMBayesianMonteCarloStressTest
```

Parameters sit in labeled blocks at the top of `main`. Baseline is a $1B fund
paying $3M a month for ten years, with the insolvency floor at $700M.

The RNG is seeded (`SEED = 42`), so the numbers below reproduce exactly. Keep the
seed fixed when comparing scenarios, so a difference in results comes from the
parameter you changed rather than from different draws.

## Results

Baseline, 1,000,000 paths. Full output in `results/`.

| Measure | Value |
| --- | --- |
| Probability of ending below the floor | 2.38% |
| 1st percentile | $588M |
| 5th percentile | $833M |
| Mean of the worst 5% | $683M |
| Median | $1.66B |
| Average | $1.73B |
| 95th percentile | $2.84B |

Three stressed scenarios, 200,000 paths each, same seed:

| Scenario | Insolvency | 5th pct | Mean worst 5% | Median |
| --- | --- | --- | --- | --- |
| Baseline | 2.4% | $833M | $683M | $1.66B |
| Outflow $5M/mo | 12.4% | $515M | $397M | $1.28B |
| Volatility 18% | 16.9% | $350M | $233M | $1.50B |
| Drift 4.5% | 11.6% | $557M | $455M | $1.19B |

The volatility row is the one worth reading. Raising volatility barely moves the
median and lifts the 95th percentile above every other scenario, while the mean
of the worst 5% falls from $683M to $233M. A fund watching only its central case
would read that change as harmless.

Which is the argument for reporting percentiles at all. The baseline average of
$1.73B says nothing about the 2.38% of paths that matter.

## Shock probabilities

Each shock's monthly probability gets a Beta prior whose strength is measured in
pseudo-months: at `priorStrength = 3000`, one simulated decade barely moves it.
That is deliberate, since ten years of history is not enough to relearn how often
pandemics happen.

Each month the probability is read off as the posterior mean,
`alpha / (alpha + beta)`; the shock is drawn as a Bernoulli trial at that
probability; then `alpha += 1` on an occurrence and `beta += 1` otherwise. The
update is exact rather than approximate because Beta is conjugate to Bernoulli,
which means there is no learning rate to tune and no clamping to keep the
probability in range.

Posteriors reset at the start of every path, since each path is one possible
history rather than a continuing record.

## Severities

Drawn from families covered in OSU STATS 3470, the original constraint on the
project, built on an inverse-transform exponential sampler.

| Shock | Family | Max monthly impact |
| --- | --- | --- |
| Recession | Negative binomial | 4% |
| War | Binomial | 3% |
| Pandemic | Gamma | 6% |
| Black swan | Negative binomial | 10% |

Combined monthly loss is capped at 25%.

## Known limitations

- Shocks are independent. Real recessions and crashes co-occur; a copula or
  regime-switching layer would capture that, and this does not.
- Returns are lognormal, so tails are thinner than real markets. The tail
  estimates here are optimistic.
- One aggregate asset. No allocation, rebalancing, or asset class split.
- Liabilities are a flat monthly number, with no mortality, retirement timing or
  inflation indexation.
- Prior base rates are illustrative, not fitted to a historical series.
- Probability is the posterior mean rather than a draw from the posterior.
  Sampling would push parameter uncertainty into the tail and widen it, but needs
  a gamma sampler for non-integer shapes. Next iteration.

Earlier versions are in `legacy/`, including one with a state-leakage bug worth
reading about.
