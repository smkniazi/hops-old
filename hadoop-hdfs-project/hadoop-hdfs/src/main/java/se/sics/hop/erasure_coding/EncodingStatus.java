package se.sics.hop.erasure_coding;

import se.sics.hop.metadata.hdfs.entity.CounterType;
import se.sics.hop.metadata.hdfs.entity.FinderType;

public class EncodingStatus {

  public static enum Status {
    NOT_ENCODED,
    ENCODING_REQUESTED,
    ENCODING_ACTIVE,
    ENCODING_CANCELED,
    ENCODING_FAILED,
    ENCODED,
    REPAIR_REQUESTED,
    REPAIR_ACTIVE,
    REPAIR_CANCELED,
    REPAIR_FAILED,
    POTENTIALLY_FIXED
  }

  public static enum Counter implements CounterType<EncodingStatus> {
    RequestedEncodings,
    ActiveEncodings,
    ActiveRepairs,
    Encoded,
    PotentiallyFixed;

    @Override
    public Class getType() {
      return EncodingStatus.class;
    }
  }

  public static enum Finder implements FinderType<EncodingStatus> {
    ByInodeId,
    LimitedByStatusRequestedEncodings,
    ByStatusActiveEncodings,
    ByStatusActiveRepairs,
    LimitedByStatusEncoded,
    LimitedByStatusRequestedRepair,
    LimitedByStatusPotentiallyFixed;

    @Override
    public Class getType() {
      return EncodingStatus.class;
    }
  }

  private long inodeId;
  private Status status;
  private EncodingPolicy encodingPolicy;
  private long modificationTime;

  public EncodingStatus() {

  }

  public EncodingStatus(Status status) {
    this.status = status;
  }

  public EncodingStatus(Status status, EncodingPolicy encodingPolicy) {
    this.status = status;
    this.encodingPolicy = encodingPolicy;
  }

  public EncodingStatus(long inodeId, Status status, EncodingPolicy encodingPolicy, long modificationTime) {
    this.inodeId = inodeId;
    this.status = status;
    this.encodingPolicy = encodingPolicy;
    this.modificationTime = modificationTime;
  }

  public long getInodeId() {
    return inodeId;
  }

  public void setInodeId(long inodeId) {
    this.inodeId = inodeId;
  }

  public long getModificationTime() {
    return modificationTime;
  }

  public void setModificationTime(long modificationTime) {
    this.modificationTime = modificationTime;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Status getStatus() {
    return status;
  }

  public EncodingPolicy getEncodingPolicy() {
    return encodingPolicy;
  }

  public void setEncodingPolicy(EncodingPolicy encodingPolicy) {
    this.encodingPolicy = encodingPolicy;
  }

  public boolean isEncoded() {
    switch (status) {
      case ENCODED:
      case REPAIR_REQUESTED:
      case REPAIR_ACTIVE:
      case REPAIR_CANCELED:
      case REPAIR_FAILED:
      case POTENTIALLY_FIXED:
        return true;
      default:
        return false;
    }
  }

  public boolean isCorrupt() {
    switch (status) {
      case REPAIR_REQUESTED:
      case REPAIR_ACTIVE:
      case REPAIR_CANCELED:
      case REPAIR_FAILED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public String toString() {
    return "EncodingStatus{" +
        "inodeId=" + inodeId +
        ", status=" + status +
        ", encodingPolicy=" + encodingPolicy +
        ", modificationTime=" + modificationTime +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EncodingStatus that = (EncodingStatus) o;

    if (inodeId != that.inodeId) return false;
    if (modificationTime != that.modificationTime) return false;
    if (encodingPolicy != null ? !encodingPolicy.equals(that.encodingPolicy) : that.encodingPolicy != null)
      return false;
    if (status != that.status) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int) (inodeId ^ (inodeId >>> 32));
    result = 31 * result + (status != null ? status.hashCode() : 0);
    result = 31 * result + (encodingPolicy != null ? encodingPolicy.hashCode() : 0);
    result = 31 * result + (int) (modificationTime ^ (modificationTime >>> 32));
    return result;
  }
}
