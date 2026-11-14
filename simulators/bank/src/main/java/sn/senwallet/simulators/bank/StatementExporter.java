package sn.senwallet.simulators.bank;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.senwallet.simulators.bank.LedgerBook.Credit;
import sn.senwallet.simulators.common.fault.FaultEffect;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class StatementExporter {

    private final S3Client s3Client;
    private final String bucket;
    private final String bankName;

    public StatementExporter(
            S3Client s3Client,
            @Value("${simulator.minio.bucket}") String bucket,
            @Value("${simulator.bank-name}") String bankName) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.bankName = bankName;
    }

    public String export(
            String account,
            LocalDate date,
            List<Credit> credits,
            FaultEffect effect) {
        ensureBucket();
        List<Credit> lines = applyFaults(credits, effect);
        String csv = toCsv(lines);
        String objectKey = bankName + "/" + account + "/" + date + ".csv";
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType("text/csv")
                        .build(),
                RequestBody.fromBytes(csv.getBytes(StandardCharsets.UTF_8)));
        return objectKey;
    }

    public String bucket() {
        return bucket;
    }

    private List<Credit> applyFaults(List<Credit> credits, FaultEffect effect) {
        if (credits.isEmpty()) {
            return credits;
        }
        if (effect instanceof FaultEffect.MissingStatementLine) {
            return credits.subList(0, credits.size() - 1);
        }
        if (effect instanceof FaultEffect.WrongAmount) {
            List<Credit> altered = new ArrayList<>(credits.size());
            for (int i = 0; i < credits.size(); i++) {
                Credit credit = credits.get(i);
                if (i == credits.size() - 1) {
                    altered.add(new Credit(
                            credit.id(),
                            credit.account(),
                            credit.reference(),
                            credit.amount() + 1,
                            credit.currency(),
                            credit.description(),
                            credit.createdAt()));
                } else {
                    altered.add(credit);
                }
            }
            return altered;
        }
        return credits;
    }

    private static String toCsv(List<Credit> credits) {
        StringBuilder sb = new StringBuilder();
        sb.append("reference,amount,currency,description,created_at\n");
        for (Credit credit : credits) {
            sb.append(credit.reference()).append(',')
                    .append(credit.amount()).append(',')
                    .append(credit.currency()).append(',')
                    .append(escape(credit.description())).append(',')
                    .append(credit.createdAt()).append('\n');
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception ex) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
}
