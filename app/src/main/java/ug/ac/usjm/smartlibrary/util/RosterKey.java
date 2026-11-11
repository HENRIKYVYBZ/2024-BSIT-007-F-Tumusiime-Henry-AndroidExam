package ug.ac.usjm.smartlibrary.util;

/**
 * The official ID list ("roster") is stored in Firestore as roster/{key}, one document per student
 * registration number or staff ID. Firestore document IDs cannot contain "/", so the key is the
 * normalised ID with "/" written as "-": 2023/bit/0457 and 2023-BIT-0457 give the same key.
 * The security rules build the same key from the profile's idNumber (idNumber.replace('/', '-')),
 * so both sides always agree.
 */
public final class RosterKey {

    private RosterKey() {
    }

    public static String of(String idNumber) {
        return Validator.normaliseRegNumber(idNumber).replace('/', '-');
    }

}
