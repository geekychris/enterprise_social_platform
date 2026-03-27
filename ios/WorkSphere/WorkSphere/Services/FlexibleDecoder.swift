import Foundation

/// A JSONDecoder subclass that automatically decodes Int64/Int from JSON strings.
/// The Java backend sends GlobalId values as strings to avoid JavaScript precision loss.
final class FlexibleDecoder: JSONDecoder {

    override func decode<T>(_ type: T.Type, from data: Data) throws -> T where T: Decodable {
        // Pre-process: convert string-encoded numbers to actual numbers in the JSON
        guard let obj = try? JSONSerialization.jsonObject(with: data) else {
            return try super.decode(type, from: data)
        }
        let converted = convertStringNumbers(obj)
        let newData = try JSONSerialization.data(withJSONObject: converted)
        return try super.decode(type, from: newData)
    }

    /// Recursively walk JSON and convert string values that look like integers to actual numbers.
    private func convertStringNumbers(_ value: Any) -> Any {
        if let dict = value as? [String: Any] {
            var result = [String: Any]()
            for (key, val) in dict {
                result[key] = convertStringNumbers(val)
            }
            return result
        } else if let array = value as? [Any] {
            return array.map { convertStringNumbers($0) }
        } else if let str = value as? String {
            // Only convert strings that are pure integers (no decimals, no other chars)
            if str.count <= 20, let intVal = Int64(str), !str.isEmpty, str.first != "0" || str == "0" {
                return NSNumber(value: intVal)
            }
            return str
        }
        return value
    }
}
