// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ProbeTuning {
  @Description("The number of seconds before the first check is performed")
  private Integer initialDelaySeconds = null;

  @Description("The number of seconds between checks")
  @SerializedName("periodSeconds")
  private Integer periodSeconds = null;

  @Description("The number of seconds with no response that indicates a failure")
  @SerializedName("timeoutSeconds")
  private Integer timeoutSeconds = null;

  @Description("Path for httpGet")
  @SerializedName("httpGetPath")
  private String httpGetPath;

  public ProbeTuning() {}

  void copyValues(ProbeTuning fromProbe) {
    if (initialDelaySeconds == null) {
      initialDelaySeconds(fromProbe.initialDelaySeconds);
    }
    if (timeoutSeconds == null) {
      timeoutSeconds(fromProbe.timeoutSeconds);
    }
    if (periodSeconds == null) {
      periodSeconds(fromProbe.periodSeconds);
    }
    if (httpGetPath == null) {
      httpGetPath(fromProbe.httpGetPath);
    }
  }

  public Integer getInitialDelaySeconds() {
    return initialDelaySeconds;
  }

  public ProbeTuning initialDelaySeconds(Integer initialDelaySeconds) {
    this.initialDelaySeconds = initialDelaySeconds;
    return this;
  }

  public Integer getPeriodSeconds() {
    return periodSeconds;
  }

  public ProbeTuning periodSeconds(Integer periodSeconds) {
    this.periodSeconds = periodSeconds;
    return this;
  }

  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public ProbeTuning timeoutSeconds(Integer timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  public String getHttpGetPath() {
    return httpGetPath;
  }

  public ProbeTuning httpGetPath(String httpGetPath) {
    this.httpGetPath = httpGetPath;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("initialDelaySeconds", initialDelaySeconds)
        .append("periodSeconds", periodSeconds)
        .append("timeoutSeconds", timeoutSeconds)
        .append("httpGetPath", httpGetPath)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ProbeTuning that = (ProbeTuning) o;

    return new EqualsBuilder()
        .append(initialDelaySeconds, that.initialDelaySeconds)
        .append(periodSeconds, that.periodSeconds)
        .append(timeoutSeconds, that.timeoutSeconds)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(initialDelaySeconds)
        .append(periodSeconds)
        .append(timeoutSeconds)
        .toHashCode();
  }
}
