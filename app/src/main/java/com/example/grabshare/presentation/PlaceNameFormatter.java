package com.example.grabshare.presentation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Tách tên chính và ngữ cảnh ngắn để trình bày địa điểm, không sửa dữ liệu Place. */
public final class PlaceNameFormatter {

    private static final Pattern BRANCH_NAME =
            Pattern.compile(
                    "(?:^|[\\s(–—-])(?:co so|chi nhanh|campus|branch|terminal|ga|cong)\\s+.+");

    /** Không tạo đối tượng vì lớp chỉ định dạng chữ hiển thị. */
    private PlaceNameFormatter() {}

    /** Hiển thị tên và ngữ cảnh trên một dòng, để TextView tự xuống dòng khi cần. */
    public static String format(String fullName) {
        return format(fullName, " · ");
    }

    /** Tách ngữ cảnh sang dòng phụ trên các ô chọn điểm và thẻ hành trình. */
    public static String formatMultiline(String fullName) {
        return format(fullName, "\n");
    }

    /** Giữ tên cơ sở hoặc số nhà, bỏ đuôi hành chính dài và phần địa chỉ lặp. */
    private static String format(String fullName, String contextSeparator) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "Chưa có tên địa điểm";
        }
        List<String> addressParts = new ArrayList<String>();
        for (String addressPart : fullName.trim().replaceAll("\\s+", " ").split(",")) {
            if (!addressPart.trim().isEmpty()) {
                addressParts.add(addressPart.trim());
            }
        }
        if (addressParts.isEmpty()) {
            return fullName.trim();
        }
        String primaryName = addressParts.get(0);
        int contextStart = 1;
        if (isHouseNumber(primaryName) && addressParts.size() > 1) {
            String streetName = addressParts.get(1);
            if (!isAdministrativeArea(streetName) && !isCountryOrPostcode(streetName)) {
                primaryName = primaryName + " " + streetName;
                contextStart = 2;
            }
        } else {
            if (addressParts.size() > 1 && isBranchName(addressParts.get(1))) {
                primaryName = primaryName + " · " + addressParts.get(1);
                contextStart = 2;
            }
        }
        String embeddedStreet = "";
        String[] nameParts = primaryName.split("\\s+[–—-]\\s+", 2);
        if (nameParts.length == 2 && isBranchName(nameParts[0]) && !isBranchName(nameParts[1])) {
            primaryName = nameParts[0];
            embeddedStreet = nameParts[1];
        }
        String streetContext = embeddedStreet;
        String areaContext = "";
        String fallbackAreaContext = "";
        for (int addressIndex = contextStart;
                addressIndex < addressParts.size();
                addressIndex = addressIndex + 1) {
            String addressPart = addressParts.get(addressIndex);
            if (isCountryOrPostcode(addressPart)) {
                if (!isHouseNumber(addressPart) || addressIndex + 1 >= addressParts.size()) {
                    continue;
                }
                String nextPart = addressParts.get(addressIndex + 1);
                if (isAdministrativeArea(nextPart) || isCountryOrPostcode(nextPart)) {
                    continue;
                }
            }
            if (isAdministrativeArea(addressPart)) {
                if (areaContext.isEmpty()
                        || administrativePriority(addressPart)
                                > administrativePriority(areaContext)) {
                    areaContext = addressPart;
                }
            } else {
                if (streetContext.isEmpty() && areaContext.isEmpty()) {
                    streetContext = addressPart;
                    if (isHouseNumber(addressPart) && addressIndex + 1 < addressParts.size()) {
                        String nextPart = addressParts.get(addressIndex + 1);
                        if (!isAdministrativeArea(nextPart) && !isCountryOrPostcode(nextPart)) {
                            streetContext = streetContext + " " + nextPart;
                            addressIndex = addressIndex + 1;
                        }
                    }
                } else {
                    fallbackAreaContext = addressPart;
                }
            }
        }
        if (areaContext.isEmpty()) {
            areaContext = fallbackAreaContext;
        }
        String context = streetContext;
        if (containsName(primaryName, streetContext)) {
            context = "";
        }
        if (!areaContext.isEmpty() && !containsName(primaryName, areaContext)) {
            if (context.isEmpty()) {
                context = areaContext;
            } else {
                if (!containsName(context, areaContext)) {
                    context = context + ", " + areaContext;
                }
            }
        }
        if (context.isEmpty() || containsName(primaryName, context)) {
            return primaryName;
        }
        return primaryName + contextSeparator + context;
    }

    /** Nhận biết số nhà riêng để không cắt mất tên đường đứng sau dấu phẩy. */
    private static boolean isHouseNumber(String value) {
        return value.matches("[0-9]+[\\p{L}0-9/.-]*");
    }

    /** Giữ cơ sở, chi nhánh, ga hoặc cổng được ghi rõ trong tên đã chọn. */
    private static boolean isBranchName(String value) {
        return BRANCH_NAME.matcher(normalize(value)).find();
    }

    /** Phân biệt thành phần hành chính với tên địa điểm và đường trong địa chỉ. */
    private static boolean isAdministrativeArea(String value) {
        String normalizedValue = normalize(value);
        return normalizedValue.matches(
                        "(?:phuong|xa|quan|huyen|thi tran|thi xa|thanh pho|tinh"
                                + "|khu pho|ap|thon|to dan pho|tp\\.?|q\\.?|p\\.?)\\s+.+")
                || normalizedValue.matches("(?:tp|q|p)\\..+")
                || normalizedValue.matches("(?:district|ward|province|city|county|town)\\s+.+")
                || normalizedValue.matches(".+\\s(?:district|ward|province|city|county|town)");
    }

    /** Ưu tiên quận hoặc huyện làm ngữ cảnh thay vì lặp nhiều cấp hành chính. */
    private static boolean isDistrict(String value) {
        String normalizedValue = normalize(value);
        return normalizedValue.matches("(?:quan|huyen|q\\.?|district|county)\\s+.+")
                || normalizedValue.matches("q\\..+")
                || normalizedValue.matches(".+\\s(?:district|county)");
    }

    /** Chọn một cấp hành chính hữu ích thay vì giữ cả khu phố, phường và thành phố. */
    private static int administrativePriority(String value) {
        if (isDistrict(value)) {
            return 3;
        }
        String normalizedValue = normalize(value);
        if (normalizedValue.matches("(?:phuong|xa|p\\.?|ward)\\s+.+")
                || normalizedValue.matches("p\\..+")
                || normalizedValue.matches(".+\\sward")) {
            return 2;
        }
        if (normalizedValue.matches("(?:khu pho|ap|thon|to dan pho)\\s+.+")) {
            return 0;
        }
        return 1;
    }

    /** Không đưa mã bưu chính và quốc gia vào nhãn ngắn của địa điểm trong nước. */
    private static boolean isCountryOrPostcode(String value) {
        String normalizedValue = normalize(value);
        return normalizedValue.equals("vietnam")
                || normalizedValue.equals("viet nam")
                || normalizedValue.equals("vn")
                || normalizedValue.matches("[0-9]{4,6}");
    }

    /** So khớp chữ đã có để tránh lặp đường hoặc khu vực trong cùng nhãn. */
    private static boolean containsName(String fullName, String component) {
        if (component.isEmpty()) {
            return false;
        }
        String normalizedFullName = normalize(fullName).replaceAll("[^\\p{L}\\p{N}]+", " ");
        String normalizedComponent = normalize(component).replaceAll("[^\\p{L}\\p{N}]+", " ");
        return (" " + normalizedFullName.trim() + " ")
                .contains(" " + normalizedComponent.trim() + " ");
    }

    /** Bỏ khác biệt dấu và hoa thường chỉ khi so khớp; giữ nguyên chữ gốc khi hiển thị. */
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
