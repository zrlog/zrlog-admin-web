import {expect, it} from "@jest/globals";
import {encodeAiMentions, findAiMention} from "./article-ai-mentions";

it("recognizes a mention at the caret without treating emails, URLs or selected text as references", () => {
    expect(findAiMention("Compare @部署 with this draft", 11)).toEqual({start: 8, end: 11, query: "部署"});
    expect(findAiMention("(@notes", 7)).toEqual({start: 1, end: 7, query: "notes"});
    for (const text of ["person@example.com", "https://site.example/@name", "@two words", "@@name"])
        expect(findAiMention(text, text.length)).toBeUndefined();
    expect(findAiMention("@notes", -1)).toBeUndefined();
    expect(findAiMention("@notes", 99)).toBeUndefined();
});

it("binds references by article ID and File identity, never by a duplicate display name", () => {
    const first = new File(["a"], "same.txt");
    const second = new File(["b"], "same.txt");
    expect(
        encodeAiMentions(
            [
                {type: "article", article: {id: 42, title: "same.txt", url: "https://blog.example/42"}},
                {type: "file", file: second},
                {type: "file", file: first},
            ],
            [second]
        )
    ).toEqual([
        {type: "article", articleId: 42},
        {type: "file", fileIndex: 0},
    ]);
});
