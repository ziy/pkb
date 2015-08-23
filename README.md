# Leveraging Procedural Knowledge for Task-oriented Search

## Citation

```
@inproceedings{Yang:2015:LPK:2766462.2767744,
 author = {Yang, Zi and Nyberg, Eric},
 title = {Leveraging Procedural Knowledge for Task-oriented Search},
 booktitle = {Proceedings of the 38th International ACM SIGIR Conference on Research and Development in Information Retrieval},
 series = {SIGIR '15},
 year = {2015},
 isbn = {978-1-4503-3621-5},
 location = {Santiago, Chile},
 pages = {513--522},
 numpages = {10},
 url = {http://doi.acm.org/10.1145/2766462.2767744},
 doi = {10.1145/2766462.2767744},
 acmid = {2767744},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {procedural knowledge base, query suggestion, search intent, search log, wikihow},
}
```

## Prerequisite
1. Clone the repository
1. Download AOL query log (i.e. user-ct-test-collection.txt.gz)
1. Download wikihow dump (i.e. wikihowcom-XXXXXXXX-current.xml): https://archive.org/details/wikihowcom
1. Uncompress all the .tar.gz files (intermediate auxiliary files)

### Steps
* Temporary files are ignored

| Order | Class | Input(s) | Output(s) |
| --- | --- | --- | --- |
| 1 | WikiHowIdSummaryExtractor | wikihowcom-XXXXXXXX-current.xml | data/wikihow-id-summary.tsv |
| 2 | QueryLogMatcher | data/wikihow-id-summary.tsv, user-ct-test-collection.txt.gz | data/log-matched-query.tsv |
| 3 | cat (concatenate) | data/log-matching-query.tsv, data/1k-additional-query.tsv | data/query.tsv |
| 4 | google-suggested-query-download | data/query.tsv | data/googlerp/ |
| 5 | bing-suggested-query-download | data/query.tsv | data/bingrp/ |
| 6 | GoogleSuggestedQueryExtractor | data/googlerp/ | data/google-suggested-query.tsv |
| 7 | BingSuggestedQueryExtractor | data/bingrp/ | data/bing-suggested-query.tsv |
| 8 | (optionally) generate a subset that has only the matched tasks | wikihowcom-XXXXXXXX-current.xml, data/query.tsv | wikihow-matched-task.xml |
| 9 | QueryTaskBicorpusConstructor | data/log-matched-query.tsv, data/google-suggested-query.tsv, data/bing-suggested-query.tsv, data/wikihow-matched-task.xml (or original), data/query.tsv | data/classify-sts-corpus.tsv |
| 10 | SearchTaskSuggestionFeatureExtractor | data/classify-sts-corpus.tsv | data/classify-sts-mallet.features, data/classify-sts-mallet.ids |
| 11 | ContextExtractor | data/query.tsv, data/googlerp/ | data/context/ |
| 12 | TaskContextBicorpusConstructor | data/classify-sts-corpus.tsv, data/context/ | data/classify-apkbc-corpus.tsv |
| 13 | AutomaticProceduralKnowledgeBaseConstructionFeatureExtractor | data/classify-apkbc-corpus.tsv | data/classify-apkbc-mallet-summary.features, data/classify-apkbc-mallet-explanation.features, data/classify-apkbc-mallet.ids |
| 14 | ClassficationExperiment | data/classify-sts-mallet.features, data/classify-sts-mallet.ids, data/classify-apkbc-mallet-summary.features, data/classify-apkbc-mallet-explanation.features, data/classify-apkbc-mallet.ids | model/model-sts.crf, model/model-apkbc-summary.crf, model/model-apkbc-explanation.crf |
| 15 | SearchTaskSuggester | data/e2e-input.tsv, data/wikihow-matched-task.xml | data/e2e-sts-result.tsv |
| 16 | AutomaticProceduralKnowledgeBaseConstructor .collectSuggestedQueries | data/e2e-input.tsv | data/e2e-apkbc-suggested-query.txt |
| 17 | google-suggested-query-download | data/e2e-apkbc-suggested-query.txt | data/e2e-googlerp/ |
| 18 | AutomaticProceduralKnowledgeBaseConstructor .downloadSearchResult | data/e2e-apkbc-suggested-query.tsv, data/e2e-googlerp/ | data/e2e-context/ |
| 19 | AutomaticProceduralKnowledgeBaseConstructor .automaticProceduralKnowledgeBaseConstruction | data/e2e-input.tsv, data/e2e-apkbc-suggested-query.tsv, data/e2e-context/ | data/e2e-apkbc-result.tsv |
