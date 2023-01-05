FROM adoptopenjdk/openjdk11

USER 1000

ENTRYPOINT ["/plagubot.suggestionsbot.runner/bin/plagubot.suggestionsbot.runner", "/config.json"]

ADD ./build/distributions/plagubot.suggestionsbot.runner.tar /
