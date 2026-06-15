CREATE TABLE mode_eligibility (
    pair_id    VARCHAR NOT NULL,
    mode       VARCHAR(100) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (pair_id, mode),
    CONSTRAINT fk_mode_eligibility_pair
        FOREIGN KEY (pair_id) REFERENCES dictionary_pair(pair_id) ON DELETE CASCADE
);

CREATE INDEX idx_mode_eligibility_pair_id ON mode_eligibility(pair_id);
