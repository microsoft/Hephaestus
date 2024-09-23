package com.hephaestus.models;

import java.util.List;
import java.util.UUID;

public class BatchReference {
    public UUID BatchId;
    public int TotalResourceCount;
    public List<NdJsonReference> Files;
    public String BatchStatus;
    public String BatchStatusUrl;
}
