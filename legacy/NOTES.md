# Earlier versions

Kept because the progression is the interesting part.

## v1, `v1_leaky_state.java`

First working version. GBM returns, four downside shocks plus a bull burst,
monthly outflow, adaptive updating of the shock probabilities.

It has a bug. `probRec`, `probWar`, `probPan` and `probBsw` are declared outside
the Monte Carlo loop and mutated inside it, so state carries from one path to the
next. Path 700,000 inherits whatever the probabilities had drifted to by the end
of path 699,999. The paths are not independent, so the ensemble is not a valid
Monte Carlo sample and the reported insolvency probability is not an estimate of
anything well defined.

Easy to miss, because nothing looks wrong. The drift is slow, the numbers stay
plausible, and it never crashes.

## v2, `v2_per_run_reset.java`

Resets the probabilities at the top of each path, which fixes the leak. Adds
clamping so an update cannot push a probability somewhere absurd, caps combined
monthly loss at 25%, and lowers the base rates.

## v3, `v3_distribution_severities.java`

Replaces uniform severity draws with a distribution per shock type: negative
binomial, binomial, gamma, built on an inverse-transform exponential sampler.

## What the current version changes

All three earlier versions update shock probabilities like this:

```java
probRec = probRec * (1 - alpha) + alpha * ((recHits + 1.0) / (month + 2.0));
```

That is an exponential moving average toward a smoothed running frequency. It
works as an adaptive heuristic, but it is not Bayesian inference. There is no
prior distribution over the parameter and no posterior, just a point estimate
getting nudged at a hand-tuned rate. Calling it Bayesian was the wrong label.

The current version keeps a Beta prior per shock and updates it exactly, `+1` to
alpha on a hit and `+1` to beta on a miss, with the probability read off as the
posterior mean. The learning rate and all four clamps became unnecessary and were
deleted, since a Beta posterior cannot leave (0,1).

Also changed: the RNG is seeded so results reproduce, and the summary reports
percentiles and the mean of the worst 5% instead of leading with an average.
