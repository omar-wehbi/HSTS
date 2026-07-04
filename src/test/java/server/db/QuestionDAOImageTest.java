package server.db;

import common.entities.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static server.db.QuestionBankTestFixture.COURSE_ALGORITHMS;

/**
 * Phase 4 — question illustrations (Person 2, scenario 2 note: a question
 * includes an illustration).
 *
 * <p>Design under test: images live in the DB (client and server are separate
 * machines, so file paths don't travel), <b>list queries never carry bytes</b>
 * (NFR 18 — lazy fetch via {@code getImage}), and on update the pair
 * ({@code imagePath}, {@code imageData}) encodes intent:
 * path+bytes = new image · path only = keep previous image · no path = no image.
 */
class QuestionDAOImageTest extends QuestionDaoTestBase {

    private static final byte[] PNG   = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4, 5};
    private static final byte[] OTHER = {9, 8, 7, 6};

    private static Question withImage(String text, String name, byte[] bytes) {
        Question q = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, text);
        q.setImagePath(name);
        q.setImageData(bytes);
        return q;
    }

    // ===== add ============================================================

    @Test
    void addStoresTheBytesAndGetImageReturnsThemIdentically() {
        Question saved = dao.add(withImage("illustrated", "diagram.png", PNG));
        assertThat(dao.getImage(saved.getId())).isEqualTo(PNG);
    }

    @Test
    void addWithoutImageMeansNullBytes() {
        Question saved = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS));
        assertThat(dao.getImage(saved.getId())).isNull();
    }

    @Test
    void getImageOfUnknownQuestionReturnsNull() {
        assertThat(dao.getImage(123456)).isNull();
    }

    // ===== lazy lists (NFR 18) ===========================================

    @Test
    void listQueriesNeverCarryImageBytes() {
        dao.add(withImage("illustrated", "diagram.png", PNG));

        assertThat(dao.getAllCurrent().get(0).getImageData())
                .as("bank lists stay light; bytes come only from getImage")
                .isNull();
        assertThat(dao.getByCourse(COURSE_ALGORITHMS).get(0).getImageData()).isNull();
        assertThat(dao.getAllCurrent().get(0).getImagePath())
                .as("the name still travels so the UI knows an image exists")
                .isEqualTo("diagram.png");
    }

    // ===== update intent rules ===========================================

    @Test
    void updateWithNewBytesReplacesTheImage() {
        Question v1 = dao.add(withImage("v1", "old.png", PNG));

        Question edit = withImage("v2", "new.png", OTHER);
        edit.setBaseId(v1.getBaseId());
        Question v2 = dao.update(edit);

        assertThat(dao.getImage(v2.getId())).isEqualTo(OTHER);
    }

    @Test
    void updateWithPathButNoBytesKeepsThePreviousImage() {
        // The client edits text only: it never downloaded the bytes, so it sends
        // the existing imagePath and null imageData — the image must survive.
        Question v1 = dao.add(withImage("v1", "keep.png", PNG));

        Question edit = withImage("v2 text change", "keep.png", null);
        edit.setBaseId(v1.getBaseId());
        Question v2 = dao.update(edit);

        assertThat(dao.getImage(v2.getId())).isEqualTo(PNG);
        assertThat(dao.getAllCurrent().get(0).getImagePath()).isEqualTo("keep.png");
    }

    @Test
    void updateWithoutPathRemovesTheImage() {
        Question v1 = dao.add(withImage("v1", "gone.png", PNG));

        Question edit = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "v2 no image");
        edit.setBaseId(v1.getBaseId());
        Question v2 = dao.update(edit);

        assertThat(dao.getImage(v2.getId())).isNull();
    }

    @Test
    void oldVersionKeepsItsOwnImageAfterAnEdit() {
        // Scenario 2.2: the previous version stays in the bank — including its
        // illustration, so the history view can show it faithfully.
        Question v1 = dao.add(withImage("v1", "old.png", PNG));

        Question edit = withImage("v2", "new.png", OTHER);
        edit.setBaseId(v1.getBaseId());
        dao.update(edit);

        assertThat(dao.getImage(v1.getId()))
                .as("the retired version's image is untouched")
                .isEqualTo(PNG);
    }
}
