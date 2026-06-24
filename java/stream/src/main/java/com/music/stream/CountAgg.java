package com.music.stream;

import com.music.common.Play;
import org.apache.flink.api.common.functions.AggregateFunction;

class CountAgg implements AggregateFunction<Play, Counts, Counts> {

  /** on new creation, default value of acc */
  @Override
  public Counts createAccumulator() {
    return new Counts();
  }

  /** acc function */
  @Override
  public Counts add(Play play, Counts counts) {
    counts.plays += 1;

    if (play.skipped()) {
      counts.skips += 1;
    }

    return counts;
  }

  /** acc return */
  @Override
  public Counts getResult(Counts accumulator) {
    return accumulator;
  }

  /** in case two things come in at once */
  @Override
  public Counts merge(Counts a, Counts b) {
    a.plays += b.plays;
    a.skips += b.skips;

    return a;
  }
}
