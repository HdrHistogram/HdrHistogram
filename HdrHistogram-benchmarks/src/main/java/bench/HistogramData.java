/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package bench;

import org.HdrHistogram.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class HistogramData {

    static Iterable<Integer> case1IntLatencies = Arrays.asList(
            1623, 1752, 3215, 1437, 154, 1358, 625, 217, 698, 6862, 1167, 1948, 1215, 665, 1372,
            889, 767, 2135, 3163, 573, 1839, 922, 475, 1233, 1013, 434, 140, 684, 400, 879,
            621, 1167, 1518, 534, 420, 9, 906, 1060, 646, 1181, 661, 2661, 844, 1132, 1169,
            614, 904, 3531, 1830, 941, 1641, 546, 729, 10, 254, 779, 1233, 499, 439, 2597,
            263, 1009, 469, 621, 1238, 1623, 2911, 380, 4654, 1105, 501, 771, 692, 3493, 2120,
            2959, 2931, 895, 835, 483, 1274, 1551, 617, 666, 1296, 1041, 2639, 10, 290, 1289,
            720, 190, 1320, 663, 520, 3646, 653, 1691, 201, 2959, 183, 2534, 632, 565, 2844,
            3421, 1645, 480, 894, 290, 1465, 1972, 752, 623, 1500, 2422, 1708, 755, 287, 1116,
            1806, 88, 676, 2118, 533, 766, 1090, 1066, 97, 437, 103, 1148, 684, 645, 2673,
            738, 1151, 757, 459, 2302, 671, 1080, 2775, 663, 762, 11448, 1442, 2726, 942, 1203,
            3435, 3509, 788, 1149, 3363, 1495, 3758, 4678, 5421, 493, 1072, 1702, 603, 1191, 726,
            3878, 866, 1136, 291, 1025, 863, 443, 786, 615, 676, 962, 136, 681, 1031, 970,
            822, 712, 735, 387, 596, 248, 1175, 275, 955, 1300, 677, 323, 408, 633, 745,
            623, 550, 522, 719, 334, 1614, 1238, 546, 296, 1090, 392, 828, 519, 2444, 257,
            973, 461, 997, 728, 748, 610, 595, 2012, 3476, 374, 2929, 429, 435, 1141, 665,
            677, 3022, 1400, 965, 406, 530, 518, 255, 435, 880, 968, 1132, 1365, 314, 2987,
            704, 688, 1398, 610, 741, 339, 1333, 746, 551, 621, 348, 571, 1420, 2360, 1099,
            485, 224, 521, 1849, 1144, 750, 2156, 1792, 11, 867, 740, 771, 1198, 625, 1202,
            894, 3372, 3061, 845, 535, 3036, 680, 2240, 1394, 1776, 1010, 3556, 647, 617, 892,
            397, 972, 99, 1848, 815, 1493, 715, 1279, 131, 1101, 1025, 10, 2963, 547, 383,
            1039, 1024, 847, 966, 1871, 341, 774, 3093, 391, 2391, 1899, 810, 172, 709, 645,
            584, 10, 2142, 1562, 549, 431, 489, 1254, 1249, 1733, 2775, 2455, 531, 413, 300,
            320, 521, 1184, 438, 564, 630, 500, 655, 530, 1028, 1575, 362, 530, 642, 526,
            542, 13, 638, 751, 830, 783, 628, 533, 599, 224, 1581, 1174, 338, 1231, 223,
            1234, 9, 19775, 2639, 1304, 754, 2010, 132, 355, 626, 717, 3014, 2053, 1667, 936,
            512, 249, 523, 604, 682, 1285, 626, 2203, 1537, 494, 462, 428, 3209, 890, 410,
            1317, 3257, 614, 2740, 265, 771, 740, 1004, 919, 543, 444, 1062, 834, 152, 1275,
            558, 2205, 428, 446, 264, 146, 503, 463, 283, 143, 816, 943, 1014, 556, 864,
            691, 648, 805, 1546, 530, 996, 1370, 1277, 506, 2140, 449, 1283, 571, 3348, 2436,
            158, 514, 1278, 537, 733, 553, 1005, 773, 565, 147, 1298, 117, 1926, 2095, 319,
            329, 3376, 539, 1495, 14459, 116, 293, 393, 143, 248, 146, 866, 1645, 1178, 208,
            13427, 1796, 461, 3538, 763, 837, 1399, 3307, 1304, 403, 585, 182, 439, 2393, 368,
            341, 385, 2114, 2919, 160, 280, 1679, 252, 1545, 919, 359, 721, 1372, 489, 451,
            1447, 684, 2414, 118, 1477, 883, 771, 1289, 655, 1975, 5604, 2918, 724, 95, 1046,
            906, 478, 412, 1688, 3305, 1063, 1115, 1518, 973, 771, 382, 1672, 1062, 3910, 1522,
            828, 346, 285, 3023, 2133, 1777, 1279, 5463, 3587, 1036, 1551, 694, 872, 150, 587,
            180, 237, 2646, 770, 3438, 1184, 859, 2672, 1377, 176, 419, 164, 1342, 1428, 605,
            743, 1060, 1451, 1193, 3126, 567, 999, 492, 562, 1027, 534, 1135, 401, 589, 767,
            849, 684, 619, 761, 701, 1707, 2210, 735, 740, 388, 2665, 1088, 426, 2530, 382,
            589, 591, 1733, 1124, 1176, 290, 1247, 2854, 534, 2120, 885, 617, 526, 394, 1266,
            3110, 9868, 822, 1551, 790, 519, 812, 721, 594, 935, 586, 1323, 700, 1632, 1084,
            932, 153, 764, 1165, 3121, 212, 178, 1362, 603, 258, 1225, 10, 465, 538, 518,
            2947, 775, 398, 2364, 306, 857, 389, 5339, 1869, 2242, 460, 146, 1045, 490, 781,
            1487, 1945, 520, 1417, 1674, 199, 1897, 2460, 941, 1446, 910, 683, 1056, 1243, 555,
            231, 752, 500, 479, 839, 1301, 1096, 226, 506, 404, 148, 1490, 776, 402, 974,
            965, 781, 464, 1117, 993, 433, 1060, 703, 1079, 1082, 528, 436, 392, 684, 820,
            10553, 326, 1150, 792, 318, 929, 2275, 2012, 520, 2732, 1024, 808, 441, 218, 510,
            961, 1940, 2418, 948, 213, 1691, 1178, 635, 170, 658, 2494, 1190, 2917, 2652, 363,
            2101, 1409, 984, 3725, 519, 420, 964, 1555, 1177, 1180, 406, 363, 484, 1652, 2729,
            642, 489, 1539, 549, 1214, 248, 1805, 679, 12352, 727, 224, 1198, 3077, 965, 848,
            348, 226, 453, 349, 493, 1134, 411, 442, 1378, 3752, 982, 938, 1483, 793, 781,
            514, 521, 1755, 2093, 440, 2101, 4215, 1004, 578, 2544, 1777, 622, 266, 9, 93,
            783, 179, 578, 3655, 675, 717, 884, 2339, 328, 1070, 1450, 1171, 1940, 1247, 2496,
            3164, 362, 786, 1903, 1606, 1428, 3027, 135, 268, 9, 8, 372, 863, 499, 233,
            1732, 337, 1116, 1152, 813, 359, 2944, 893, 261, 620, 375, 2891, 391, 2858, 1569,
            499, 2672, 601, 883, 92, 954, 522, 517, 1831, 637, 670, 1163, 487, 459, 1246,
            685, 741, 906, 880, 2245, 1805, 579, 1077, 693, 727, 708, 1301, 10, 1470, 351,
            2872, 744, 129, 1852, 657, 1403, 869, 460, 4478, 2549, 437, 873, 719, 3370, 552,
            1075, 1586, 1271, 152, 1303, 10, 8, 230, 1105, 837, 450, 206, 735, 747, 562,
            1039, 2065, 1894, 115, 976, 1180, 3171, 693, 801, 1199, 690, 519, 592, 147, 2180,
            726, 1457, 759, 392, 1068, 1934, 398, 601, 669, 458, 918, 466, 861, 481, 1786,
            22603, 483, 2211, 633, 597, 631, 481, 746, 1118, 476, 439, 466, 327, 1104, 825,
            305, 17, 1141, 453, 3389, 324, 782, 866, 2637, 657, 468, 3432, 474, 1046, 493,
            2082, 557, 588, 445, 293, 427, 622, 1902, 3047, 10, 337, 899, 489, 295, 1041,
            837, 831, 1065, 314, 1147, 502, 1013, 4753, 908, 1007, 1449, 762, 181, 1529, 908,
            3910, 471, 2920, 397, 668, 922, 493, 3339, 1030, 1270, 1805, 613, 847, 601, 893,
            508, 114, 2248, 2936, 2297, 289, 830, 578, 360, 4733, 536, 2042, 1998, 875, 477,
            479, 592, 205, 1574, 1305, 849, 660, 4853, 2138, 404, 514, 1210, 1130, 1268, 629,
            236, 308, 464, 2297, 221, 964, 1185, 710, 4388, 464, 866, 575, 1417, 4019, 1189,
            1296, 835, 186, 886, 2276, 890, 1168, 3468, 767, 1283, 394, 736, 530, 699, 398,
            1830, 1833, 680, 1324, 3636, 1922, 230, 480, 1949, 844, 1388, 519, 1062, 681, 673,
            942, 359, 458, 639, 1139, 1267, 686, 1771, 1452, 94, 973, 530, 1134, 1186, 512,
            1254, 412, 456, 1710, 960, 650, 197, 1283, 791, 105, 1521, 1196, 396, 3583, 1619,
            2497, 1027, 593, 744, 1191, 715, 442, 1425, 1112, 580, 1158, 2397, 622, 2720, 704,
            887, 544, 4209, 607, 5361, 505, 678, 482, 933, 1359, 602, 1547, 80, 9, 2596,
            715, 1823, 1730, 1842, 146, 966, 454, 1014, 2916, 552, 1174, 915, 275, 1429, 1850,
            309, 553, 372, 492, 619, 1405, 1772, 204, 975, 584, 3240, 3176, 3573, 3409, 769,
            436, 2069, 103, 338, 553, 1118, 1338, 1353, 635, 572, 340, 645, 2607, 1128, 580,
            2349, 1063, 2740, 12, 202, 570, 1162, 1326, 1973, 205, 693, 2568, 340, 711, 1230,
            115, 558, 3503, 201, 759, 1859, 919, 601, 657, 1333, 229, 2940, 463, 1045, 498,
            736, 741, 633, 532, 201, 886, 1001, 917, 164, 1064, 2585, 2512, 1078, 809, 1953,
            2231, 550, 1044, 1247, 1404, 759, 808, 1079, 771, 547, 1161, 1349, 509, 534, 239,
            675, 580, 560, 507, 952, 408, 522, 1959, 1776, 1836, 253, 3580, 2106, 4104, 486,
            678, 1723, 1535, 1369, 802, 473, 236, 1918, 668, 895, 1037, 688, 1114, 594, 1038,
            817, 258, 973, 2231, 1193, 1117, 449, 728, 482, 3180, 1107, 3624, 1041, 666, 973,
            475, 1083, 254, 650, 373, 2535, 666, 3343, 713, 998, 374, 204, 581, 503, 726,
            293, 1585, 1038, 480, 451, 2074, 600, 522, 761, 656, 332, 638, 815, 734, 1358,
            506, 1846, 324, 364, 538, 452, 479, 667, 2720, 240, 734, 466, 609, 352, 305,
            83, 13569, 247, 1061, 163, 1886, 1125, 634, 1647, 419, 219, 2116, 1123, 1094, 523,
            504, 125, 1951, 2731, 1183, 2495, 461, 189, 722, 1652, 2321, 2597, 1040, 662, 2841,
            323, 69, 999, 659, 915, 701, 881, 492, 1148, 2408, 1623, 1612, 1015, 478, 2229,
            1368, 1006, 1643, 1780, 1942, 806, 3176, 458, 1711, 11, 1100, 790, 1500, 591, 519,
            2294, 1718, 358, 733, 427, 509, 609, 1305, 579, 2443, 2342, 2869, 1490, 939, 628,
            10, 10, 108, 1048, 1815, 233, 691, 1071, 1348, 1995, 16, 667, 373, 2780, 10,
            607, 1197, 696, 1715, 1051, 2094, 2801, 1204, 606, 3048, 1523, 856, 1295, 175, 906,
            445, 930, 6525, 590, 659, 626, 2403, 630, 886, 556, 87, 1515, 408, 1820, 437,
            366, 690, 683, 373, 2664, 4202, 709, 1035, 677, 3500, 442, 1005, 632, 582, 3749,
            597, 790, 1137, 1652, 2091, 372, 1325, 227, 190, 249, 441, 1839, 1046, 607, 776,
            534, 1502, 10, 669, 827, 567, 1765, 744, 2889, 489, 486, 447, 503, 511, 9,
            857, 3319, 1119, 10, 4683, 797, 224, 441, 10990, 984, 850, 653, 3790, 1105, 4321,
            940, 686, 910, 260, 1393, 266, 923, 3213, 14929, 1525, 2679, 672, 964, 226, 268,
            897, 2579, 3039, 941, 623, 702, 1585, 91, 2207, 985, 759, 859, 2541, 1208, 539,
            2264, 1033, 823, 953, 421, 934, 496, 1717, 455, 653, 1833, 699, 626, 651, 1206,
            102, 2865, 453, 137, 631, 513, 183, 561, 727, 606, 2350, 467, 1519, 1089, 1270,
            349, 1649, 560, 576, 934, 924, 294, 366, 2666, 498, 411, 913, 707, 262, 419,
            1003, 543, 475, 1169, 152, 217, 521, 221, 2239, 952, 526, 514, 2414, 387, 771,
            739, 1600, 503, 123, 948, 2078, 390, 1675, 563, 1470, 4583, 537, 2501, 557, 3184,
            589, 503, 1853, 2247, 2131, 2687, 621, 2180, 760, 977, 698, 2333, 1849, 12, 6816,
            1042, 3926, 2414, 158, 361, 278, 2074, 1204, 1812, 918, 441, 974, 1803);

    static ArrayList<Long> case1Latencies = new ArrayList<Long>();
    static {
        for (int latency : case1IntLatencies) {
            case1Latencies.add((long)latency);
        }
    }

    static ArrayList<Long> case2Latencies = new ArrayList<Long>();
    static {
        for (long i = 1; i <= 10000;  i++) {
            case2Latencies.add(1000 * i);
        }
    }

    static ArrayList<Long> case3Latencies = new ArrayList<Long>();
    static {
        for (long i = 1; i <= 100000;  i++) {
            case3Latencies.add(1000 * i);
        }
    }

    static ArrayList<Long> sparsed1Latencies = new ArrayList<Long>();
    static {
        for (long i = 1; i <= 5;  i++) {
            sparsed1Latencies.add((long) Math.pow(i, i));
        }
    }

    static ArrayList<Long> sparsed2Latencies = new ArrayList<Long>();
    static {
        for (long i = 1; i <= 8;  i++) {
            sparsed2Latencies.add((long) Math.pow(i, i));
        }
    }

    static ArrayList<Long> quadratic = new ArrayList<Long>();
    static {
        for (long i = 1; i <= 10000;  i++) {
            long value = (long)Math.pow(i, 2);
            if (value < Integer.MAX_VALUE) {
                quadratic.add(value);
            }
        }
    }

    static ArrayList<Long> cubic = new ArrayList<Long>();
    static {
        for (long i = 1; i <= 10000;  i++) {
            long value = (long)Math.pow(i, 3);
            if (value < Integer.MAX_VALUE) {
                cubic.add(value);
            }
        }
    }

    static ArrayList<Long> case1PlusSparsed2 = new ArrayList<Long>();
    static {
        case1PlusSparsed2.addAll(case1Latencies);
        case1PlusSparsed2.addAll(sparsed2Latencies);
    }

    static ArrayList<Long> longestjHiccupLine = new ArrayList<Long>();

    static ArrayList<Long> shortestjHiccupLine = new ArrayList<Long>();

    static ArrayList<Long> sumOfjHiccupLines = new ArrayList<Long>();

    static {
        InputStream readerStream = HistogramData.class.getResourceAsStream("jHiccup-2.0.6.logV1.hlog");
        HistogramLogReader reader = new HistogramLogReader(readerStream);
        Histogram histogram;
        long maxCount = 0;
        long minCount = Long.MAX_VALUE;
        Histogram longestHistogram = null;
        Histogram shortestHistogram = null;
        Histogram accumulatedHistigram = new Histogram(3);
        while ((histogram = (Histogram) reader.nextIntervalHistogram()) != null) {
            if (histogram.getTotalCount() == 0) {
                continue;
            }
            if (histogram.getTotalCount() > maxCount) {
                longestHistogram = histogram;
                maxCount = histogram.getTotalCount();
            }
            if (histogram.getTotalCount() < minCount) {
                shortestHistogram = histogram;
                minCount = histogram.getTotalCount();
            }
            accumulatedHistigram.add(histogram);
        }
        if (longestHistogram != null) {
            for (HistogramIterationValue v : longestHistogram.recordedValues()) {
                for (long i = 0; i < v.getCountAtValueIteratedTo(); i++) {
                    longestjHiccupLine.add(v.getValueIteratedTo());
                }
            }
        }
        if (shortestHistogram != null) {
            for (HistogramIterationValue v : shortestHistogram.recordedValues()) {
                for (long i = 0; i < v.getCountAtValueIteratedTo(); i++) {
                    shortestjHiccupLine.add(v.getValueIteratedTo());
                }
            }
        }
        for (HistogramIterationValue v : accumulatedHistigram.recordedValues()) {
            for (long i = 0; i < v.getCountAtValueIteratedTo(); i++) {
                sumOfjHiccupLines.add(v.getValueIteratedTo());
            }
        }
    }

    static final HashMap<String, Iterable<Long>> data = new HashMap<String, Iterable<Long>>();

    static {
        data.put("case1", case1Latencies);
        data.put("case2", case2Latencies);
        data.put("case3", case3Latencies);
        data.put("sparsed1", sparsed1Latencies);
        data.put("sparsed2", sparsed2Latencies);
        data.put("case1PlusSparsed2", case1PlusSparsed2);
        data.put("quadratic", quadratic);
        data.put("cubic", cubic);
        data.put("longestjHiccupLine", longestjHiccupLine);
        data.put("shortestjHiccupLine", shortestjHiccupLine);
        data.put("sumOfjHiccupLines", sumOfjHiccupLines);
    }

    static {
        System.out.println();
        for (String seriesName :
                new String[] {
                        "case1", "case2", "case3", "sparsed1", "sparsed2", "quadratic", "cubic",
                        "case1PlusSparsed2", "longestjHiccupLine", "shortestjHiccupLine", "sumOfjHiccupLines"
                }) {
            Iterable<Long> latencies = data.get(seriesName);
            for (int digits = 2; digits <= 3; digits++) {
                Histogram histogram = new Histogram(digits);
                SkinnyHistogram skinnyHistogram = new SkinnyHistogram(digits);
                for (long latency : latencies) {
                    histogram.recordValueWithCount(latency, 1);
                    skinnyHistogram.recordValueWithCount(latency, 1);
                }
                ByteBuffer buffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
                int histogramBytes = histogram.encodeIntoByteBuffer(buffer);
                buffer.rewind();
                int histogramCompressedBytes = histogram.encodeIntoCompressedByteBuffer(buffer);
                buffer.rewind();
                int skinnyBytes = skinnyHistogram.encodeIntoByteBuffer(buffer);
                buffer.rewind();
                int skinnyCompressBytes = skinnyHistogram.encodeIntoCompressedByteBuffer(buffer);
                System.out.format(
                        "%20s [%1d] (Histogram/Skinny/%%Reduction): " +
                                "[%6d /%6d /%7.2f%%]   %5d /%5d /%7.2f%%\n",
                        seriesName, digits,
                        histogramBytes, skinnyBytes,
                        (100.0 - 100.0 * (histogramBytes / (skinnyBytes * 1.0))),
                        histogramCompressedBytes, skinnyCompressBytes,
                        (100.0 - 100.0 * (histogramCompressedBytes / (skinnyCompressBytes * 1.0)))
                );
            }
        }
    }
}
