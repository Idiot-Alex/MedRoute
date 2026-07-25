INSERT INTO poi_keyword (poi_id, release_id, keyword)
SELECT
    candidate.id,
    candidate.release_id,
    fixture_keyword.keyword
FROM poi candidate
JOIN (
    SELECT 'P-ENTRANCE' AS poi_code, 'menzhenrukou' AS keyword
    UNION ALL SELECT 'P-ENTRANCE', 'mzrk'
    UNION ALL SELECT 'P-REGISTRATION', 'menzhendatingfuwutai'
    UNION ALL SELECT 'P-REGISTRATION', 'fuwutai'
    UNION ALL SELECT 'P-REGISTRATION', 'mzdtfwt'
    UNION ALL SELECT 'P-LAB-2F', 'jianyanke'
    UNION ALL SELECT 'P-LAB-2F', 'jyk'
    UNION ALL SELECT 'P-LAB-2F', 'chouxue'
    UNION ALL SELECT 'P-ULTRASOUND-3F', 'chaoshengyixueke'
    UNION ALL SELECT 'P-ULTRASOUND-3F', 'chaosheng'
    UNION ALL SELECT 'P-ULTRASOUND-3F', 'csyxk'
) fixture_keyword
    ON fixture_keyword.poi_code = candidate.code
WHERE NOT EXISTS (
    SELECT 1
    FROM poi_keyword existing
    WHERE existing.poi_id = candidate.id
      AND existing.keyword = fixture_keyword.keyword
);
